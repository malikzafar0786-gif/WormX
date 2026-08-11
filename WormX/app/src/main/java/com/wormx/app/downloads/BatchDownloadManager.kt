package com.wormx.app.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.util.Patterns

/** Takes a multi-line block of pasted text and queues every valid URL it contains. */
object BatchDownloadManager {

    fun extractUrls(rawText: String): List<String> =
        rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && Patterns.WEB_URL.matcher(it).matches() }
            .distinct()

    fun queueAll(context: Context, urls: List<String>) {
        urls.forEach { url ->
            val guessedName = url.substringAfterLast('/').ifBlank { "file_${System.currentTimeMillis()}" }
            val id = DownloadRepository.newId()
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_START
                putExtra(DownloadForegroundService.EXTRA_ITEM_ID, id)
                putExtra(DownloadForegroundService.EXTRA_URL, url)
                putExtra(DownloadForegroundService.EXTRA_FILENAME, guessedName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
