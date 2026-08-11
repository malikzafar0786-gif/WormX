package com.wormx.app.downloads

import java.io.File

enum class DownloadState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/**
 * One tracked download. [bytesDownloaded] is persisted so a paused/killed
 * download can resume from where it left off via an HTTP Range request.
 */
data class DownloadItem(
    val id: String,
    val url: String,
    var fileName: String,
    var totalBytes: Long = -1L,
    var bytesDownloaded: Long = 0L,
    var state: DownloadState = DownloadState.QUEUED,
    var destinationPath: String? = null,
    var mimeType: String? = null,
    var speedBytesPerSec: Long = 0L
) {
    val progressPercent: Int
        get() = if (totalBytes <= 0) 0 else ((bytesDownloaded * 100) / totalBytes).toInt()

    val speedLabel: String
        get() = when {
            speedBytesPerSec <= 0 -> ""
            speedBytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0))
            else -> "%.0f KB/s".format(speedBytesPerSec / 1024.0)
        }

    val destinationFile: File?
        get() = destinationPath?.let { File(it) }

    /** Category used for folder sorting + vault icon selection. */
    val fileCategory: FileCategory
        get() = FileCategory.fromFileName(fileName)
}

enum class FileCategory(val folderName: String) {
    VIDEO("Videos"),
    IMAGE("Images"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    APK("Apps"),
    OTHER("Other");

    companion object {
        fun fromFileName(name: String): FileCategory {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mov", "mkv", "webm", "avi", "3gp" -> VIDEO
                "jpg", "jpeg", "png", "webp", "gif", "heic" -> IMAGE
                "mp3", "wav", "m4a", "aac", "ogg" -> AUDIO
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> DOCUMENT
                "zip", "rar", "7z", "tar", "gz" -> ARCHIVE
                "apk" -> APK
                else -> OTHER
            }
        }
    }
}
