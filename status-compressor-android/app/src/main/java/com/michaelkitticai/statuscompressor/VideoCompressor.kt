package com.michaelkitticai.statuscompressor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max

data class VideoInfo(val width: Int, val height: Int, val durationMs: Long)

data class VideoPart(val file: File, val durationMs: Long)

/** x264 stopped making progress or failed; the export is retried with the phone's encoder. */
class X264FailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class VideoPlan(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val parts: Int,
    val sharpen: Float,
    val x264Preset: String,
)

@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {

    fun probe(uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // Some files (streamed or fragmented MP4s, like many stock videos) report 0 here.
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0 } ?: durationFromSamples(uri)
            if (w == null || h == null) throw IllegalArgumentException("Couldn't read this video.")
            // Phones store portrait video as landscape plus a rotation flag.
            return if (rotation == 90 || rotation == 270) VideoInfo(h, w, duration) else VideoInfo(w, h, duration)
        } finally {
            r.release()
        }
    }

    /** Time of the last video sample, or 0 if unknown (the video is then exported whole). */
    private fun durationFromSamples(uri: Uri): Long {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return 0
            val format = ex.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 0) {
                return format.getLong(MediaFormat.KEY_DURATION) / 1000
            }
            ex.selectTrack(track)
            // Fragmented MP4s can't seek to the end, so walk every sample (headers only, quick).
            var last = 0L
            while (ex.sampleTime >= 0) {
                last = max(last, ex.sampleTime)
                if (!ex.advance()) break
            }
            last / 1000
        } catch (e: Exception) {
            0
        } finally {
            ex.release()
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
        return VideoPlan(w, h, bitrate.toInt(), parts, preset.videoSharpen, preset.x264Preset)
    }

    /**
     * One bilinear resize from 4K straight to 720p only samples a few of every nine pixels, which
     * causes shimmer and jagged edges that the next encoder (ours, then WhatsApp's) wastes bits on.
     * Halving in steps averages every pixel, like a proper downscale filter.
     */
    private fun downscaleSteps(info: VideoInfo, plan: VideoPlan): List<Presentation> {
        val steps = mutableListOf<Presentation>()
        var w = info.width
        var h = info.height
        while (w >= plan.width * 2 && h >= plan.height * 2) {
            w = (w / 4) * 2
            h = (h / 4) * 2
            steps += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)
        }
        steps += Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT)
        return steps
    }

    companion object {
        /** No new x264 frame for this long means it's stuck. */
        private const val STALL_MS = 30_000L
    }

    /** Exports [plan.parts] files, calling [onProgress] with 0..1 across all parts. */
    suspend fun compress(
        uri: Uri,
        info: VideoInfo,
        plan: VideoPlan,
        outFile: (part: Int) -> File,
        onProgress: (Float) -> Unit,
        onFallback: () -> Unit = {},
    ): List<VideoPart> {
        var useX264 = X264.available
        val partMs = info.durationMs / plan.parts
        val outputs = mutableListOf<VideoPart>()
        for (i in 0 until plan.parts) {
            val start = i * partMs
            val end = if (i == plan.parts - 1) info.durationMs else (i + 1) * partMs
            val file = outFile(i)
            // A single part is exported whole; clipping to a bad duration produced empty exports.
            val clip = if (plan.parts > 1) start to end else null
            val progress = { p: Float -> onProgress((i + p) / plan.parts) }
            try {
                export(uri, info, clip, plan, file, useX264, progress)
            } catch (e: X264FailedException) {
                // Better a video from the phone's encoder than none at all.
                Log.w(TAG, "x264 failed, retrying with the phone's encoder", e)
                useX264 = false
                onFallback()
                export(uri, info, clip, plan, file, useX264 = false, progress)
            }
            outputs += VideoPart(file, end - start)
        }
        return outputs
    }

    private suspend fun export(
        uri: Uri,
        info: VideoInfo,
        clip: Pair<Long, Long>?,
        plan: VideoPlan,
        out: File,
        useX264: Boolean,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Export ${info.width}x${info.height} ${info.durationMs} ms, clip $clip -> $plan, x264 $useX264")
        // Transformer must be created, driven and cancelled on a Looper thread.
        val handler = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine<Unit> { cont ->
            val hardware = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    // No CBR: some encoders (MediaTek) reject it and the export fails outright.
                    // VBR may undershoot the bitrate, which only makes the fallback a bit softer.
                    VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build()
                )
                // Keeps the whole file (video + audio) under WhatsApp's re-encode limit.
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(96_000).build())
                .setEnableFallback(true)
                .build()
            // x264 when available; the phone's encoder is the fallback.
            // Transformer can't report progress for files that don't store their length (many
            // stock videos), so also track how far x264 has got.
            val spanUs = ((clip?.let { it.second - it.first } ?: info.durationMs) * 1000).coerceAtLeast(1)
            val firstPtsUs = AtomicLong(C.TIME_UNSET)
            val encodedUs = AtomicLong(0)
            // Frames in or out of x264; the watchdog only gives up when neither moves.
            val activity = AtomicLong(0)
            val x264Settings = X264Settings(
                bitrate = plan.bitrate,
                maxBitrate = (plan.bitrate * 1.07).toInt(),
                preset = plan.x264Preset,
                onFrameEncoded = { ptsUs ->
                    firstPtsUs.compareAndSet(C.TIME_UNSET, ptsUs)
                    encodedUs.accumulateAndGet(ptsUs - firstPtsUs.get()) { a, b -> max(a, b) }
                    activity.incrementAndGet()
                },
                onFrameIn = { activity.incrementAndGet() },
            )
            val encoders = if (useX264) X264EncoderFactory(x264Settings, hardware) else hardware

            lateinit var poll: Runnable
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoders)
                // 4K decode plus tone mapping can stall longer than the 10 s default and fail with
                // ERROR_CODE_MUXING_TIMEOUT on slower phones. Let it take as long as it needs.
                .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
                // Encode portrait as portrait (720x1280), like the clips WhatsApp left untouched,
                // instead of landscape plus a rotation flag.
                .setPortraitEncodingEnabled(useX264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.i(TAG, "Export done: ${out.length()} bytes, encoder ${exportResult.videoEncoderName}")
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        Log.e(TAG, "Export failed", exportException)
                        handler.removeCallbacks(poll)
                        if (cont.isActive) {
                            val message = "Couldn't convert this video (${exportException.errorCodeName})."
                            cont.resumeWithException(
                                if (useX264) X264FailedException(message, exportException)
                                else IllegalStateException(message, exportException)
                            )
                        }
                    }
                })
                .build()

            val mediaItem = MediaItem.Builder().setUri(uri).apply {
                if (clip != null) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.first)
                            .setEndPositionMs(clip.second)
                            .build()
                    )
                }
            }.build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        listOf(),
                        downscaleSteps(info, plan) + listOfNotNull(
                            plan.sharpen.takeIf { it > 0f }?.let { SharpenEffect(it) },
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
            var lastActivity = -1L
            var lastActivityMs = SystemClock.elapsedRealtime()
            poll = Runnable {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                } else if (encodedUs.get() > 0) {
                    onProgress(minOf(0.99f, encodedUs.get().toFloat() / spanUs))
                }
                // Watchdog: x264 should turn out frames steadily. If nothing comes for a while,
                // something is stuck; give up on x264 and let compress() retry without it.
                val now = SystemClock.elapsedRealtime()
                if (activity.get() != lastActivity) {
                    lastActivity = activity.get()
                    lastActivityMs = now
                }
                if (useX264 && now - lastActivityMs > STALL_MS) {
                    Log.w(TAG, "x264 stalled: no frame in or out for ${STALL_MS / 1000} s (encoded up to ${encodedUs.get()} us)")
                    transformer.cancel()
                    out.delete()
                    if (cont.isActive) cont.resumeWithException(X264FailedException("x264 stalled"))
                    return@Runnable
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
