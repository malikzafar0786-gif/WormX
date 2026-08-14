package com.wormx.app.vault

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.wormx.app.downloads.DownloadItem
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

data class VaultEntry(
    val id: String,
    val displayName: String,
    val encryptedPath: String,
    val originalCategory: String
)

data class ImportResult(val success: Boolean, val errorReason: String? = null)

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

    /**
     * Imports an existing gallery photo/video (picked via the system photo
     * picker) into the vault: copies its bytes in, encrypts them, then
     * deletes the original so it actually disappears from the gallery
     * rather than just being duplicated.
     *
     * Returns a reason on failure instead of a bare boolean — some OEM
     * gallery pickers hand back URIs that don't grant read access the
     * standard way, and a silent "couldn't import" with no reason makes
     * that impossible to diagnose from the outside.
     */
    fun hideFromGallery(uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: ""
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: uri.lastPathSegment?.substringAfterLast('.', "")
            ?: "dat"
        val category = when {
            mimeType.startsWith("video/") -> "VIDEO"
            mimeType.startsWith("image/") -> "IMAGE"
            else -> "OTHER"
        }

        val entryId = UUID.randomUUID().toString()
        val tempPlain = File(context.cacheDir, "$entryId.$extension")

        try {
            // Photo Picker URIs come with a temporary read grant that's valid
            // for this synchronous copy — no persistable permission is
            // needed, and requesting one throws IllegalArgumentException on
            // these URIs on several OEM builds, which was aborting every
            // import. Just read the file directly.
            val opened = try {
                resolver.openInputStream(uri)
            } catch (e: Exception) {
                android.util.Log.e("VaultRepository", "openInputStream failed for $uri", e)
                return ImportResult(false, e.javaClass.simpleName)
            }

            if (opened == null) {
                return ImportResult(false, "No access to that file")
            }

            opened.use { input ->
                tempPlain.outputStream().use { output -> input.copyTo(output) }
            }

            val encrypted = File(vaultDir, "$entryId.enc")
            crypto.encryptFileIntoVault(tempPlain, encrypted) // also deletes tempPlain

            entries.add(
                VaultEntry(
                    id = entryId,
                    displayName = "imported_$entryId.$extension",
                    encryptedPath = encrypted.absolutePath,
                    originalCategory = category
                )
            )
            notifyListeners()

            // Remove the original so it's genuinely hidden, not just copied.
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                // Some providers (e.g. newer MediaStore scoped-storage cases)
                // require a user confirmation dialog to delete on their
                // behalf; the file is safely inside the vault either way,
                // it just may still show in the gallery until the user
                // deletes it there too.
            }
            return ImportResult(true)
        } catch (e: Exception) {
            android.util.Log.e("VaultRepository", "Import failed for $uri", e)
            tempPlain.delete()
            return ImportResult(false, e.javaClass.simpleName)
        }
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
