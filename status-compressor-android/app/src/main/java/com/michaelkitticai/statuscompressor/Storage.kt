package com.michaelkitticai.statuscompressor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import java.io.File
import java.util.Locale

object Storage {

    private const val ALBUM = "Status Compressor"

    fun outDir(context: Context): File = File(context.cacheDir, "out").apply { mkdirs() }

    /** Compressed copies live in cache for sharing; drop ones from previous days. */
    fun cleanOld(context: Context) {
        val cutoff = System.currentTimeMillis() - 48 * 3600 * 1000L
        outDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copies [file] into Pictures/ or Movies/ "Status Compressor". No permission needed on Android 10+. */
    fun saveToGallery(context: Context, file: File, isVideo: Boolean): Uri? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/$ALBUM",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun nameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        if (size < 0) {
            size = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        return (name ?: uri.lastPathSegment ?: "media") to size
    }
}

/** Running totals across every file ever compressed, kept between launches. */
class Stats(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)

    var files by mutableIntStateOf(prefs.getInt("files", 0))
        private set
    var originalBytes by mutableLongStateOf(prefs.getLong("original", 0))
        private set
    var outputBytes by mutableLongStateOf(prefs.getLong("output", 0))
        private set

    val savedBytes get() = originalBytes - outputBytes

    fun record(original: Long, output: Long) {
        files += 1
        originalBytes += original
        outputBytes += output
        prefs.edit {
            putInt("files", files)
            putLong("original", originalBytes)
            putLong("output", outputBytes)
        }
    }
}

fun formatBytes(bytes: Long): String {
    val abs = kotlin.math.abs(bytes).toDouble()
    val sign = if (bytes < 0) "-" else ""
    return when {
        abs < 1024 -> "$sign${abs.toLong()} B"
        abs < 1024 * 1024 -> String.format(Locale.US, "%s%.0f KB", sign, abs / 1024)
        abs < 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%s%.1f MB", sign, abs / (1024 * 1024))
        else -> String.format(Locale.US, "%s%.2f GB", sign, abs / (1024.0 * 1024 * 1024))
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms + 500) / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
