package com.wormx.app.downloads

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wormx.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Keeps the process (and therefore any running DownloadEngine coroutines) alive
 * when the user backgrounds/minimizes the app — this is the fix for the
 * reference app's "download stops when minimized" problem.
 */
class DownloadForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private val engines = mutableMapOf<String, DownloadEngine>()
    private val repository by lazy { DownloadRepository.getInstance(applicationContext) }

    companion object {
        const val CHANNEL_ID = "wormx_downloads"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.wormx.app.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.wormx.app.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.wormx.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_REFERER = "extra_referer"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("WormX is active", "Watching for downloads"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return START_STICKY
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "download"
                val referer = intent.getStringExtra(EXTRA_REFERER)
                startOrResume(id, url, fileName, referer)
            }
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { engines[it]?.pause() }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { engines[it]?.cancel() }
        }
        return START_STICKY
    }

    private fun startOrResume(id: String, url: String, fileName: String, referer: String? = null) {
        val item = repository.getOrCreate(id, url, fileName)
        if (referer != null) item.referer = referer
        val engine = DownloadEngine(
            onProgress = { downloaded, total, speedBps ->
                item.bytesDownloaded = downloaded
                item.totalBytes = total
                item.speedBytesPerSec = speedBps
                repository.update(item)
                updateNotification(item)
            },
            onStateChange = { state ->
                item.state = state
                repository.update(item)
                if (state == DownloadState.COMPLETED) {
                    // Runs on the same IO dispatcher the engine's coroutine
                    // is already on at this point — safe to do the blocking
                    // file copy here directly.
                    MediaStorePublisher.publish(applicationContext, item)
                }
                if (state == DownloadState.COMPLETED || state == DownloadState.CANCELLED) {
                    engines.remove(id)
                    if (engines.isEmpty()) stopSelf()
                }
            }
        )
        engines[id] = engine
        engine.start(item, scope)
    }

    private fun updateNotification(item: DownloadItem) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIF_ID,
            buildNotification(item.fileName, "${item.progressPercent}% • ${item.state}")
        )
    }

    private fun buildNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
