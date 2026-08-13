package com.wormx.app.downloads

/** One selectable quality/format variant of a resolved media link. */
data class QualityOption(
    val label: String,       // e.g. "1080p", "720p", "Audio only (MP3)"
    val url: String,
    val approxSizeBytes: Long = -1L,
    val isAudioOnly: Boolean = false,
    val referer: String? = null // origin page the video URL came from
)
