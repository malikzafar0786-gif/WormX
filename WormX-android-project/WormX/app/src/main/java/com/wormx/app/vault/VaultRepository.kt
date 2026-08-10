package com.wormx.app.vault

import android.content.Context
import com.wormx.app.downloads.DownloadItem
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class VaultEntry(
    val id: String,
    val displayName: String,
    val encryptedPath: String,
    val originalCategory: String
)

/**
 * Moves a completed download into the encrypted vault folder and keeps the
 * in-memory list that VaultGridActivity renders.
 */
class VaultRepository private constructor(private val context: Context) {

    private val crypto = VaultCryptoManager(context)
    private val entries = CopyOnWriteArrayList<VaultEntry>()
    private val listeners = mutableListOf<(List<VaultEntry>) -> Unit>()

    private val vaultDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    fun hideDownload(item: DownloadItem) {
        val source = item.destinationFile ?: return
        if (!source.exists()) return

        val encrypted = File(vaultDir, "${item.id}.enc")
        crypto.encryptFileIntoVault(source, encrypted)

        entries.add(
            VaultEntry(
                id = item.id,
                displayName = item.fileName,
                encryptedPath = encrypted.absolutePath,
                originalCategory = item.fileCategory.name
            )
        )
        notifyListeners()
    }

    fun restoreToDownloads(entry: VaultEntry) {
        entries.remove(entry)
        // Decrypt back out to the public Downloads location, then delete the vault copy.
        val encryptedFile = File(entry.encryptedPath)
        val restored = File(context.getExternalFilesDir("Downloads"), entry.displayName)
        crypto.decryptFileFromVault(encryptedFile, restored)
        encryptedFile.delete()
        notifyListeners()
    }

    fun all(): List<VaultEntry> = entries.toList()

    fun observe(listener: (List<VaultEntry>) -> Unit) {
        listeners.add(listener)
        listener(all())
    }

    private fun notifyListeners() {
        val snapshot = all()
        listeners.forEach { it(snapshot) }
    }

    companion object {
        @Volatile private var instance: VaultRepository? = null
        fun getInstance(context: Context): VaultRepository =
            instance ?: synchronized(this) {
                instance ?: VaultRepository(context.applicationContext).also { instance = it }
            }
    }
}
