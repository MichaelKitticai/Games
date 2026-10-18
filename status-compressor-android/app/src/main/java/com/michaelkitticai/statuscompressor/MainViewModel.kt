package com.michaelkitticai.statuscompressor

import android.app.Application
import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Kind { IMAGE, VIDEO, OTHER }

class Output(val file: File, val uri: Uri, val size: Long, val label: String)

class Item(
    val id: Long,
    val source: Uri,
    val name: String,
    val kind: Kind,
    val originalSize: Long,
    val preset: Preset,
) {
    var status by mutableStateOf("Waiting...")
    var progress by mutableStateOf<Float?>(null)
    var failed by mutableStateOf(false)
    var done by mutableStateOf(false)
    var detail by mutableStateOf("")
    var outputs by mutableStateOf<List<Output>>(emptyList())
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var skipped = false

    val outputSize: Long get() = outputs.sumOf { it.size }
    val shareMime: String get() = if (kind == Kind.VIDEO) "video/mp4" else "image/jpeg"
    val busy: Boolean get() = !done && !failed
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val video = VideoCompressor(app)
    private val queue = Channel<Item>(Channel.UNLIMITED)
    private var nextId = 0L
    private var currentId = -1L
    private var currentJob: Job? = null

    val items = mutableStateListOf<Item>()
    val stats = Stats(app)

    var preset by mutableStateOf(
        Preset.entries.firstOrNull { it.name == prefs.getString("preset", null) } ?: Preset.WHATSAPP_HD
    )
        private set

    var saveToGallery by mutableStateOf(prefs.getBoolean("gallery", true))
        private set

    init {
        Storage.cleanOld(app)
        // One at a time: video encoding uses the phone's single hardware encoder.
        viewModelScope.launch {
            for (item in queue) {
                if (item.skipped) continue
                val job = launch { process(item) }
                currentId = item.id
                currentJob = job
                job.join()
                currentJob = null
                currentId = -1
            }
        }
    }

    fun selectPreset(p: Preset) {
        preset = p
        prefs.edit { putString("preset", p.name) }
    }

    fun updateSaveToGallery(on: Boolean) {
        saveToGallery = on
        prefs.edit { putBoolean("gallery", on) }
    }

    fun add(uris: List<Uri>) {
        val chosen = preset
        viewModelScope.launch {
            for (uri in uris) {
                val item = withContext(Dispatchers.IO) { createItem(uri, chosen) }
                items.add(0, item)
                if (item.kind == Kind.OTHER) {
                    item.failed = true
                    item.status = "Not a photo or video"
                } else {
                    queue.send(item)
                }
                launch(Dispatchers.IO) { item.thumb = loadThumb(item) }
            }
        }
    }

    fun cancel(item: Item) {
        if (item.id == currentId) {
            currentJob?.cancel()
        } else {
            item.skipped = true
            item.failed = true
            item.status = "Cancelled"
        }
    }

    fun clearFinished() {
        items.removeAll { !it.busy }
    }

    private fun createItem(uri: Uri, preset: Preset): Item {
        val (name, size) = Storage.nameAndSize(context, uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val kind = when {
            mime.startsWith("video/") -> Kind.VIDEO
            mime.startsWith("image/") -> Kind.IMAGE
            Regex("""\.(mp4|mov|m4v|3gp|webm|mkv)$""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> Kind.VIDEO
            Regex("""\.(jpe?g|png|webp|heic|heif|gif|bmp)$""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> Kind.IMAGE
            else -> Kind.OTHER
        }
        return Item(nextId++, uri, name, kind, size, preset)
    }

    private suspend fun process(item: Item) {
        try {
            when (item.kind) {
                Kind.IMAGE -> processImage(item)
                Kind.VIDEO -> processVideo(item)
                Kind.OTHER -> Unit
            }
        } catch (e: CancellationException) {
            item.progress = null
            item.failed = true
            item.status = "Cancelled"
            throw e
        } catch (e: OutOfMemoryError) {
            item.progress = null
            item.failed = true
            item.status = "Too large for this phone's memory"
        } catch (e: Exception) {
            item.progress = null
            item.failed = true
            item.status = e.message ?: "Something went wrong"
        }
    }

    private suspend fun processImage(item: Item) {
        item.status = "Compressing..."
        val out = uniqueFile("${baseName(item.name)}_${item.preset.fileTag}", "jpg")
        val r = withContext(Dispatchers.Default) {
            ImageCompressor.compress(context, item.source, item.preset, item.originalSize, out)
        }
        item.detail = "${r.srcWidth}×${r.srcHeight} → ${r.width}×${r.height}"
        finish(item, listOf(Output(out, Storage.shareUri(context, out), out.length(), "Photo")))
    }

    private suspend fun processVideo(item: Item) {
        item.status = "Reading video..."
        val info = withContext(Dispatchers.IO) { video.probe(item.source) }
        val plan = video.plan(info, item.preset, item.originalSize)
        item.detail = "${info.width}×${info.height} · ${formatDuration(info.durationMs)} → " +
            "${plan.width}×${plan.height}" + if (plan.parts > 1) " · ${plan.parts} parts" else ""
        item.status = "Compressing..."
        item.progress = 0f

        val base = baseName(item.name)
        val parts = video.compress(
            uri = item.source,
            info = info,
            plan = plan,
            outFile = { i ->
                val suffix = if (plan.parts > 1) "_part${i + 1}" else ""
                uniqueFile("${base}_${item.preset.fileTag}$suffix", "mp4")
            },
            onProgress = { p ->
                item.progress = p
                if (plan.parts > 1) {
                    val part = minOf(plan.parts, (p * plan.parts).toInt() + 1)
                    item.status = "Compressing part $part of ${plan.parts}..."
                }
            },
        )
        val outputs = parts.mapIndexed { i, part ->
            val label = if (parts.size > 1) "Part ${i + 1} · ${formatDuration(part.durationMs)}"
            else formatDuration(part.durationMs)
            Output(part.file, Storage.shareUri(context, part.file), part.file.length(), label)
        }
        finish(item, outputs)
    }

    private suspend fun finish(item: Item, outputs: List<Output>) {
        item.progress = null
        item.outputs = outputs
        if (saveToGallery) {
            withContext(Dispatchers.IO) {
                outputs.forEach { Storage.saveToGallery(context, it.file, item.kind == Kind.VIDEO) }
            }
        }
        stats.record(item.originalSize, item.outputSize)
        item.done = true
        item.status = if (saveToGallery) "Done · saved to gallery" else "Done"
    }

    private fun uniqueFile(base: String, ext: String): File {
        val dir = Storage.outDir(context)
        var f = File(dir, "$base.$ext")
        var n = 2
        while (f.exists()) f = File(dir, "${base}_${n++}.$ext")
        return f
    }

    private fun baseName(name: String) =
        name.substringBeforeLast('.').replace(Regex("""[^\w\-]+"""), "_").ifBlank { "status" }

    private fun loadThumb(item: Item): ImageBitmap? {
        try {
            return context.contentResolver.loadThumbnail(item.source, Size(240, 320), null).asImageBitmap()
        } catch (_: Exception) {
            // Not every provider supports thumbnails; fall through to decoding ourselves.
        }
        return try {
            when (item.kind) {
                Kind.VIDEO -> {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(context, item.source)
                        r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 320)?.asImageBitmap()
                    } finally {
                        r.release()
                    }
                }
                Kind.IMAGE -> ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.source)) { d, info, _ ->
                    d.setTargetSampleSize(maxOf(1, minOf(info.size.width, info.size.height) / 240))
                }.asImageBitmap()
                Kind.OTHER -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
