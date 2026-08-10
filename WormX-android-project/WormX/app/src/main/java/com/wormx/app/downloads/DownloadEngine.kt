package com.wormx.app.downloads

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Handles a single file transfer with true pause/resume support.
 *
 * Resume works by re-issuing the request with a `Range: bytes=<offset>-` header
 * pointing at how much of the destination file already exists on disk — this is
 * the piece the original reference app was missing, which is why closing it
 * killed the download instead of pausing it.
 */
class DownloadEngine(
    private val onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    private val onStateChange: (DownloadState) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived streaming download
        .build()

    @Volatile private var paused = false
    @Volatile private var cancelled = false
    private var job: Job? = null

    fun start(item: DownloadItem, scope: CoroutineScope) {
        paused = false
        cancelled = false
        job = scope.launch(Dispatchers.IO) { runDownload(item) }
    }

    fun pause() {
        paused = true
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    private suspend fun runDownload(item: DownloadItem) {
        val destFile = File(item.destinationPath ?: return)
        destFile.parentFile?.mkdirs()

        var existingBytes = if (destFile.exists()) destFile.length() else 0L

        try {
            onStateChange(DownloadState.RUNNING)

            val requestBuilder = Request.Builder().url(item.url)
            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    onStateChange(DownloadState.FAILED)
                    return
                }

                // Server may not support Range (200 instead of 206) -> restart from 0
                val resumed = response.code == 206
                if (!resumed) existingBytes = 0L

                val body = response.body ?: run {
                    onStateChange(DownloadState.FAILED)
                    return
                }

                val contentLength = body.contentLength()
                val total = if (contentLength > 0) existingBytes + contentLength else item.totalBytes

                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.seek(existingBytes)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = existingBytes
                        while (true) {
                            if (cancelled) {
                                onStateChange(DownloadState.CANCELLED)
                                return
                            }
                            if (paused) {
                                onStateChange(DownloadState.PAUSED)
                                return // bytes already flushed to disk == our resume point
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                onStateChange(DownloadState.COMPLETED)
            }
        } catch (e: Exception) {
            Log.e("DownloadEngine", "Download failed for ${item.fileName}", e)
            if (!paused && !cancelled) onStateChange(DownloadState.FAILED)
        }
    }
}
