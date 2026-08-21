package com.wormx.app.downloads

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for in-flight and completed downloads.
 * Kept intentionally simple (in-memory + a lightweight listener list) — swap
 * for a Room database if you need history to survive a full process restart.
 */
class DownloadRepository private constructor(private val context: Context) {

    private val items = ConcurrentHashMap<String, DownloadItem>()
    private val listeners = mutableListOf<(List<DownloadItem>) -> Unit>()
    private val removedIds = java.util.concurrent.CopyOnWriteArraySet<String>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun getOrCreate(id: String, url: String, fileName: String): DownloadItem {
        return items.getOrPut(id) {
            val category = FileCategory.fromFileName(fileName)
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                category.folderName
            )
            DownloadItem(
                id = id,
                url = url,
                fileName = fileName,
                destinationPath = File(dir, fileName).absolutePath
            )
        }
    }

    fun update(item: DownloadItem) {
        // A cancel/remove can race with an in-flight progress callback from
        // the download engine (it may not notice `cancelled` until its
        // current blocking read returns). Without this check, that late
        // update re-inserts the very item the user just removed — the
        // "cancelled item comes back for several seconds" bug.
        if (item.id in removedIds) return
        items[item.id] = item
        notifyListeners()
    }

    fun remove(id: String) {
        removedIds.add(id)
        items.remove(id)
        notifyListeners()
    }

    fun all(): List<DownloadItem> = items.values.sortedByDescending { it.id }

    fun observe(listener: (List<DownloadItem>) -> Unit) {
        listeners.add(listener)
        listener(all())
    }

    private fun notifyListeners() {
        val snapshot = all()
        // Progress callbacks originate on a background (IO) thread inside
        // DownloadEngine — touching RecyclerView/adapter state from there is
        // what made the UI feel slow/janky. Always hand off to the main
        // thread here so every listener is called safely, regardless of
        // which thread triggered the update.
        mainHandler.post {
            listeners.forEach { it(snapshot) }
        }
    }

    companion object {
        @Volatile private var instance: DownloadRepository? = null
        fun getInstance(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }

        fun newId(): String = UUID.randomUUID().toString()
    }
}
