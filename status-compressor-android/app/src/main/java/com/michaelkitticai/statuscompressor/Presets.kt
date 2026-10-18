package com.michaelkitticai.statuscompressor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Each target is sized to what that app delivers to viewers, so the app's own
 * re-encode has as little work to do as possible: no big downscale, no frame
 * rate conversion, no huge bitrate drop.
 */
enum class Preset(
    val label: String,
    val description: String,
    val fileTag: String,
    // Photos
    val imageLongEdge: Int,
    val imageShortEdge: Int,
    val imageMaxBytes: Long,
    // Videos
    val videoShortEdge: Int,
    val videoLongEdge: Int,
    val videoMaxBitrate: Int,
    val videoMaxBytesPerPart: Long?,
    val segmentSeconds: Int?,
) {
    WHATSAPP_HD(
        label = "WhatsApp HD",
        description = "Sharpest Status. Tap HD in WhatsApp before posting. Long videos are split into 60 s parts.",
        fileTag = "wa_hd",
        imageLongEdge = 2560, imageShortEdge = 2560, imageMaxBytes = 3_500_000,
        videoShortEdge = 1080, videoLongEdge = 1920, videoMaxBitrate = 6_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = 60,
    ),
    WHATSAPP(
        label = "WhatsApp",
        description = "Standard quality, smaller files. Good when data is tight.",
        fileTag = "wa",
        imageLongEdge = 1600, imageShortEdge = 1600, imageMaxBytes = 1_000_000,
        videoShortEdge = 720, videoLongEdge = 1280, videoMaxBitrate = 3_500_000,
        videoMaxBytesPerPart = 16_000_000, segmentSeconds = 60,
    ),
    INSTAGRAM(
        label = "Instagram",
        description = "Stories, Reels and posts at 1080p, the size Instagram shows them.",
        fileTag = "ig",
        imageLongEdge = 1920, imageShortEdge = 1080, imageMaxBytes = 4_000_000,
        videoShortEdge = 1080, videoLongEdge = 1920, videoMaxBitrate = 8_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = null,
    ),
    TIKTOK(
        label = "TikTok",
        description = "1080p at a high bitrate. Turn on \"Upload HD\" in TikTok's settings.",
        fileTag = "tt",
        imageLongEdge = 1920, imageShortEdge = 1080, imageMaxBytes = 4_000_000,
        videoShortEdge = 1080, videoLongEdge = 1920, videoMaxBitrate = 10_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = null,
    );

    fun fitImage(w: Int, h: Int) = fit(w, h, imageShortEdge, imageLongEdge, even = false)

    fun fitVideo(w: Int, h: Int) = fit(w, h, videoShortEdge, videoLongEdge, even = true)

    companion object {
        /** Scales down (never up) so both edges fit, keeping aspect ratio. */
        fun fit(w: Int, h: Int, shortMax: Int, longMax: Int, even: Boolean): Pair<Int, Int> {
            val scale = min(1.0, min(shortMax.toDouble() / min(w, h), longMax.toDouble() / max(w, h)))
            fun round(v: Double): Int =
                if (even) max(2, (v / 2).roundToInt() * 2) else max(1, v.roundToInt())
            return round(w * scale) to round(h * scale)
        }
    }
}
