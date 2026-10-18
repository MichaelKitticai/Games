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
    val videoSharpen: Float,
) {
    WHATSAPP_HD(
        label = "WhatsApp HD",
        description = "Sharpest Status. Videos are made at WhatsApp's 720p HD size so it doesn't shrink them again. " +
            "Turn on HD in WhatsApp (Settings > Storage and data > Media upload quality). Long videos are split into 60 s parts.",
        fileTag = "wa_hd",
        imageLongEdge = 2560, imageShortEdge = 2560, imageMaxBytes = 3_500_000,
        // WhatsApp HD Status plays at 720p. Sending 1080p or 4K just means WhatsApp does the
        // downscale itself, quickly and badly, then re-encodes at a low bitrate.
        videoShortEdge = 720, videoLongEdge = 1280, videoMaxBitrate = 4_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = 60, videoSharpen = 0.5f,
    ),
    WHATSAPP(
        label = "WhatsApp",
        description = "Standard quality, smaller files. Good when data is tight.",
        fileTag = "wa",
        imageLongEdge = 1600, imageShortEdge = 1600, imageMaxBytes = 1_000_000,
        // WhatsApp's standard (non-HD) Status is about 480p.
        videoShortEdge = 480, videoLongEdge = 854, videoMaxBitrate = 2_000_000,
        videoMaxBytesPerPart = 16_000_000, segmentSeconds = 60, videoSharpen = 0.4f,
    ),
    INSTAGRAM(
        label = "Instagram",
        description = "Stories, Reels and posts at 1080p, the size Instagram shows them.",
        fileTag = "ig",
        imageLongEdge = 1920, imageShortEdge = 1080, imageMaxBytes = 4_000_000,
        videoShortEdge = 1080, videoLongEdge = 1920, videoMaxBitrate = 8_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = null, videoSharpen = 0.25f,
    ),
    TIKTOK(
        label = "TikTok",
        description = "1080p at a high bitrate. Turn on \"Upload HD\" in TikTok's settings.",
        fileTag = "tt",
        imageLongEdge = 1920, imageShortEdge = 1080, imageMaxBytes = 4_000_000,
        videoShortEdge = 1080, videoLongEdge = 1920, videoMaxBitrate = 10_000_000,
        videoMaxBytesPerPart = null, segmentSeconds = null, videoSharpen = 0.25f,
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
