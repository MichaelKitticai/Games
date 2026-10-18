package com.michaelkitticai.statuscompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

data class ImageResult(val srcWidth: Int, val srcHeight: Int, val width: Int, val height: Int)

object ImageCompressor {

    /** Unsharp-mask strength. Mild, so edges get crisper without halos. */
    private const val SHARPEN_AMOUNT = 0.45f

    /** Differences below this are treated as noise and left alone. */
    private const val SHARPEN_THRESHOLD = 3

    fun compress(context: Context, uri: Uri, preset: Preset, originalSize: Long, out: File): ImageResult {
        var srcW = 0
        var srcH = 0
        var w = 0
        var h = 0
        // ImageDecoder applies EXIF rotation and reads HEIC on every supported device.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            srcW = info.size.width
            srcH = info.size.height
            val fitted = preset.fitImage(srcW, srcH)
            w = fitted.first
            h = fitted.second
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }

        val bitmap = flattenOntoWhite(decoded)
        sharpen(bitmap)

        // If we didn't need to shrink it, don't hand back something bigger than the original.
        val resized = w != srcW || h != srcH
        val maxBytes = if (resized) preset.imageMaxBytes else minOf(preset.imageMaxBytes, originalSize)
        var quality = 92
        var bytes = encode(bitmap, quality)
        while (bytes.size > maxBytes && quality > 80) {
            quality -= 3
            bytes = encode(bitmap, quality)
        }
        bitmap.recycle()
        out.writeBytes(bytes)
        return ImageResult(srcW, srcH, w, h)
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /** JPEG has no transparency; without this, transparent PNGs turn black. */
    private fun flattenOntoWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        src.recycle()
        return out
    }

    /**
     * Unsharp mask with a separable 3x3 Gaussian. The threshold keeps flat areas
     * (sky, skin) from getting grainy, since noise is what apps compress worst.
     */
    private fun sharpen(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        val tmp = IntArray(w * h)
        val blur = IntArray(w * h)

        for (shift in intArrayOf(16, 8, 0)) {
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val l = if (x > 0) x - 1 else x
                    val r = if (x < w - 1) x + 1 else x
                    tmp[row + x] = (px[row + l] shr shift and 0xFF) +
                        2 * (px[row + x] shr shift and 0xFF) +
                        (px[row + r] shr shift and 0xFF)
                }
            }
            for (y in 0 until h) {
                val up = (if (y > 0) y - 1 else y) * w
                val row = y * w
                val down = (if (y < h - 1) y + 1 else y) * w
                for (x in 0 until w) {
                    blur[row + x] = tmp[up + x] + 2 * tmp[row + x] + tmp[down + x]
                }
            }
            val mask = (0xFF shl shift).inv()
            for (i in px.indices) {
                val orig = px[i] shr shift and 0xFF
                val diff = orig - blur[i] / 16f
                if (diff > SHARPEN_THRESHOLD || diff < -SHARPEN_THRESHOLD) {
                    val v = (orig + SHARPEN_AMOUNT * diff).toInt().coerceIn(0, 255)
                    out[i] = (out[i] and mask) or (v shl shift)
                }
            }
        }
        bitmap.setPixels(out, 0, w, 0, 0, w, h)
    }
}
