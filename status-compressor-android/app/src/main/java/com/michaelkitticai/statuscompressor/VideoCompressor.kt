package com.michaelkitticai.statuscompressor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max

data class VideoInfo(val width: Int, val height: Int, val durationMs: Long)

data class VideoPart(val file: File, val durationMs: Long)

data class VideoPlan(val width: Int, val height: Int, val bitrate: Int, val parts: Int)

@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {

    fun probe(uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (w == null || h == null || duration == null) throw IllegalArgumentException("Couldn't read this video.")
            // Phones store portrait video as landscape plus a rotation flag.
            return if (rotation == 90 || rotation == 270) VideoInfo(h, w, duration) else VideoInfo(w, h, duration)
        } finally {
            r.release()
        }
    }

    fun plan(info: VideoInfo, preset: Preset, originalSize: Long): VideoPlan {
        val (w, h) = preset.fitVideo(info.width, info.height)
        val durationS = info.durationMs / 1000.0
        // Split evenly (70 s at 60 s max -> 2 x 35 s) rather than leaving a tiny tail.
        val parts = preset.segmentSeconds?.let { max(1, ceil(durationS / it - 0.01).toInt()) } ?: 1
        val partS = durationS / parts

        var bitrate = preset.videoMaxBitrate.toDouble()
        preset.videoMaxBytesPerPart?.let { cap ->
            val budget = cap * 8 * 0.92 / partS - 160_000
            bitrate = minOf(bitrate, max(1_200_000.0, budget))
        }
        // Re-encoding a lean source at a higher bitrate only makes the file bigger.
        if (durationS > 0) {
            val sourceBitrate = originalSize * 8 / durationS
            bitrate = minOf(bitrate, max(1_000_000.0, sourceBitrate))
        }
        return VideoPlan(w, h, bitrate.toInt(), parts)
    }

    /** Exports [plan.parts] files, calling [onProgress] with 0..1 across all parts. */
    suspend fun compress(
        uri: Uri,
        info: VideoInfo,
        plan: VideoPlan,
        outFile: (part: Int) -> File,
        onProgress: (Float) -> Unit,
    ): List<VideoPart> {
        val partMs = info.durationMs / plan.parts
        val outputs = mutableListOf<VideoPart>()
        for (i in 0 until plan.parts) {
            val start = i * partMs
            val end = if (i == plan.parts - 1) info.durationMs else (i + 1) * partMs
            val file = outFile(i)
            export(uri, start, end, plan, file) { p -> onProgress((i + p) / plan.parts) }
            outputs += VideoPart(file, end - start)
        }
        return outputs
    }

    private suspend fun export(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        plan: VideoPlan,
        out: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        // Transformer must be created, driven and cancelled on a Looper thread.
        val handler = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine<Unit> { cont ->
            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build()
                )
                .setEnableFallback(true)
                .build()

            lateinit var poll: Runnable
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoders)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    "Couldn't convert this video (${exportException.errorCodeName}).",
                                    exportException,
                                )
                            )
                        }
                    }
                })
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        listOf(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT
                            ),
                            // 60 fps phone footage at 30 fps: apps drop frames anyway, and do it worse.
                            FrameDropEffect.createDefaultFrameDropEffect(30f),
                        ),
                    )
                )
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(listOf(edited)))
                // Most phones record HDR now; apps show it washed out. Convert it properly first.
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            val holder = ProgressHolder()
            poll = Runnable {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                handler.postDelayed(poll, 250)
            }

            transformer.start(composition, out.absolutePath)
            handler.postDelayed(poll, 250)

            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                    out.delete()
                }
            }
        }
    }
}
