package com.michaelkitticai.statuscompressor

import android.media.MediaCodec
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.transformer.Codec
import androidx.media3.transformer.ExportException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** JNI bindings for libsc264 (x264). */
object X264 {
    val available: Boolean = try {
        System.loadLibrary("sc264")
        true
    } catch (e: Throwable) {
        false
    }

    @JvmStatic external fun nativeOpen(
        width: Int, height: Int, fps: Int, bitrateKbps: Int, maxrateKbps: Int, bufsizeKbps: Int,
        preset: String, tune: String,
    ): Long

    @JvmStatic external fun nativeHeaders(handle: Long): ByteArray?
    @JvmStatic external fun nativeMaxDelayedFrames(handle: Long): Int
    @JvmStatic external fun nativeConvert(handle: Long, rgba: ByteBuffer, rowStride: Int, i420: ByteBuffer)
    @JvmStatic external fun nativeEncode(handle: Long, i420: ByteBuffer, ptsUs: Long, info: LongArray): ByteArray?
    @JvmStatic external fun nativeFlush(handle: Long, info: LongArray): ByteArray?
    @JvmStatic external fun nativeClose(handle: Long)

    fun open(width: Int, height: Int, fps: Int, settings: X264Settings): Long = nativeOpen(
        width, height, fps,
        settings.bitrate / 1000, settings.maxBitrate / 1000, settings.maxBitrate * 2 / 1000,
        settings.preset, settings.tune,
    )

    /**
     * How many frames may be in the encoder at once without the GPU outrunning it. x264 holds up to
     * its delay (lookahead, B-frames, threads) before returning anything, plus a few in our queue.
     */
    fun maxFramesInEncoder(width: Int, height: Int, fps: Int, settings: X264Settings): Int {
        val handle = open(width, height, fps, settings)
        if (handle == 0L) return C.INDEX_UNSET
        return try {
            nativeMaxDelayedFrames(handle) + QUEUED_FRAMES
        } finally {
            nativeClose(handle)
        }
    }

    const val QUEUED_FRAMES = 6
}

/** x264 settings for one export. */
data class X264Settings(
    val bitrate: Int,
    val maxBitrate: Int,
    val preset: String = "slow",
    val tune: String = "film",
    /** Called on the encoder thread with each encoded frame's timestamp, for progress. */
    val onFrameEncoded: ((ptsUs: Long) -> Unit)? = null,
)

/**
 * Uses x264 for H.264 video, and the phone's encoders for everything else (or if x264 isn't
 * available). At WhatsApp's bitrates x264 is far sharper than phone hardware encoders.
 */
@OptIn(UnstableApi::class)
class X264EncoderFactory(
    private val settings: X264Settings,
    private val fallback: Codec.EncoderFactory,
) : Codec.EncoderFactory {

    override fun createForAudioEncoding(format: Format, logSessionId: android.media.metrics.LogSessionId?): Codec =
        fallback.createForAudioEncoding(format, logSessionId)

    override fun createForVideoEncoding(format: Format, logSessionId: android.media.metrics.LogSessionId?): Codec {
        if (!X264.available || format.sampleMimeType != MimeTypes.VIDEO_H264) {
            return fallback.createForVideoEncoding(format, logSessionId)
        }
        Log.i(TAG, "x264 encoder for ${format.width}x${format.height} @ ${format.frameRate} fps, $settings")
        return X264Codec(format, settings)
    }

    override fun videoNeedsEncoding() = true
}

/**
 * A [Codec] that looks like a MediaCodec surface encoder to Media3: the frame pipeline renders
 * into a [GlFrameReader] surface, frames are converted to YUV and encoded by x264 on a worker
 * thread, and the muxer pulls the encoded frames back out.
 */
@OptIn(UnstableApi::class)
private class X264Codec(requested: Format, settings: X264Settings) : Codec {

    private val onFrameEncoded = settings.onFrameEncoded
    private var framesEncoded = 0

    private class Frame(val yuv: ByteBuffer?, val ptsUs: Long) // yuv == null: end of stream
    private class Output(val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    private val width = requested.width
    private val height = requested.height
    private val fps = requested.frameRate.takeIf { it > 0 }?.let { minOf(60, Math.round(it)) } ?: 30

    private val configurationFormat = requested.buildUpon()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
        .build()

    private val handle: Long = X264.open(width, height, fps, settings).also { if (it == 0L) throw ExportException.createForUnexpected(IllegalStateException("x264 failed to open")) }

    private val outputFormat: Format

    // Transformer limits frames in flight (see X264.maxFramesInEncoder), so these stay small.
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    private val frames = LinkedBlockingQueue<Frame>()
    private val reader = GlFrameReader(width, height) { rgba, ptsUs ->
        val yuv = pool.poll() ?: ByteBuffer.allocateDirect(width * height * 3 / 2)
        X264.nativeConvert(handle, rgba, width * 4, yuv)
        frames.add(Frame(yuv, ptsUs))
    }
    private val outputs = ConcurrentLinkedQueue<Output>()
    @Volatile private var encoderDone = false
    @Volatile private var error: Throwable? = null
    @Volatile private var released = false
    private val encoderThread = Thread(::encodeLoop, "x264-encode")

    init {
        val headers = X264.nativeHeaders(handle) ?: ByteArray(0)
        val (sps, pps) = splitParameterSets(headers)
        outputFormat = configurationFormat.buildUpon()
            .setInitializationData(listOf(sps, pps))
            .setAverageBitrate(settings.bitrate)
            .build()
        encoderThread.start()
    }

    private fun encodeLoop() {
        val info = LongArray(3)
        try {
            while (!released) {
                val frame = frames.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val yuv = frame.yuv
                if (yuv == null) {
                    while (true) emit(X264.nativeFlush(handle, info) ?: break, info)
                    Log.i(TAG, "x264 done: $framesEncoded frames")
                    encoderDone = true
                    return
                }
                val encoded = X264.nativeEncode(handle, yuv, frame.ptsUs, info)
                pool.add(yuv)
                if (encoded != null) emit(encoded, info)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "x264 encoder thread failed", t)
            error = t
        }
    }

    private fun emit(data: ByteArray, info: LongArray) {
        val bufferInfo = MediaCodec.BufferInfo().apply {
            set(0, data.size, info[0], if (info[2] != 0L) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        }
        outputs.add(Output(ByteBuffer.wrap(data), bufferInfo))
        framesEncoded++
        if (framesEncoded == 1 || framesEncoded % 60 == 0) Log.i(TAG, "x264 output: $framesEncoded frames encoded")
        onFrameEncoded?.invoke(info[0])
    }

    private fun checkError() {
        error?.let { throw ExportException.createForUnexpected(Exception("x264 encoding failed", it)) }
    }

    override fun getConfigurationFormat() = configurationFormat
    override fun getName() = "x264"
    override fun getInputSurface(): Surface = reader.surface
    override fun getInputFormat() = configurationFormat

    override fun maybeDequeueInputBuffer(inputBuffer: DecoderInputBuffer): Boolean =
        throw UnsupportedOperationException("x264 takes input from its surface")

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) =
        throw UnsupportedOperationException("x264 takes input from its surface")

    override fun signalEndOfInputStream() {
        // Every frame was rendered to the surface before this call; end once they're all read.
        reader.afterPendingFrames { frames.add(Frame(null, C.TIME_END_OF_SOURCE)) }
    }

    override fun getOutputFormat(): Format {
        checkError()
        return outputFormat
    }

    override fun getOutputBuffer(): ByteBuffer? {
        checkError()
        return outputs.peek()?.data
    }

    override fun getOutputBufferInfo(): MediaCodec.BufferInfo? = outputs.peek()?.info

    override fun releaseOutputBuffer(render: Boolean) {
        outputs.poll()
    }

    override fun releaseOutputBuffer(renderPresentationTimeUs: Long) {
        outputs.poll()
    }

    override fun isEnded() = encoderDone && outputs.isEmpty()

    override fun release() {
        if (released) return
        released = true
        encoderThread.join(2000)
        reader.release()
        X264.nativeClose(handle)
    }

    companion object {
        /** Splits x264's Annex B headers into SPS and PPS (each keeping its start code), for csd-0/csd-1. */
        fun splitParameterSets(annexB: ByteArray): Pair<ByteArray, ByteArray> {
            val starts = mutableListOf<Int>()
            var i = 0
            while (i + 3 < annexB.size) {
                if (annexB[i].toInt() == 0 && annexB[i + 1].toInt() == 0 && annexB[i + 2].toInt() == 0 && annexB[i + 3].toInt() == 1) {
                    starts += i
                    i += 4
                } else i++
            }
            var sps = ByteArray(0)
            var pps = ByteArray(0)
            starts.forEachIndexed { n, start ->
                val end = if (n + 1 < starts.size) starts[n + 1] else annexB.size
                val nal = annexB.copyOfRange(start, end)
                when (annexB[start + 4].toInt() and 0x1f) {
                    7 -> sps = nal
                    8 -> pps = nal
                }
            }
            return sps to pps
        }
    }
}
