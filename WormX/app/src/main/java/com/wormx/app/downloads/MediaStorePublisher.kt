package com.wormx.app.downloads

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File

/**
 * A download finishing successfully isn't useful if the person can never
 * find the file afterwards — writing only into the app's private storage
 * (as the initial version did) meant nothing showed up in Gallery or the
 * Files app, which from the outside just looks like "downloads don't work."
 *
 * This copies the finished file into the appropriate public MediaStore
 * collection (Movies/Pictures/Downloads) so it appears in Gallery/Downloads
 * immediately, the same moment the reference app shows "Saved to gallery ✓".
 * The private copy is left in place too, since Vault's "move to vault"
 * still operates on that original path.
 */
object MediaStorePublisher {

    fun publish(context: Context, item: DownloadItem): Boolean {
        val sourceFile = item.destinationFile ?: return false
        if (!sourceFile.exists()) return false

        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(sourceFile.extension.lowercase())
            ?: "application/octet-stream"

        return try {
            when (item.fileCategory) {
                FileCategory.VIDEO -> publishToCollection(
                    context, sourceFile, mimeType,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES,
                    MediaStore.Video.Media.RELATIVE_PATH,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.IS_PENDING
                )
                FileCategory.IMAGE -> publishToCollection(
                    context, sourceFile, mimeType,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.IS_PENDING
                )
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        publishToCollection(
                            context, sourceFile, mimeType,
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            Environment.DIRECTORY_DOWNLOADS,
                            MediaStore.Downloads.RELATIVE_PATH,
                            MediaStore.Downloads.DISPLAY_NAME,
                            MediaStore.Downloads.MIME_TYPE,
                            MediaStore.Downloads.IS_PENDING
                        )
                    } else {
                        // MediaStore.Downloads doesn't exist before API 29;
                        // on those older versions the public Downloads
                        // folder is writable directly given
                        // WRITE_EXTERNAL_STORAGE (requested at runtime).
                        publishLegacyPreQ(context, sourceFile)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("MediaStorePublisher", "Failed to publish ${sourceFile.name} to gallery", e)
            false
        }
    }

    private fun publishToCollection(
        context: Context,
        sourceFile: File,
        mimeType: String,
        collectionUri: android.net.Uri,
        relativeDirValue: String,
        relativeDirKey: String,
        displayNameKey: String,
        mimeTypeKey: String,
        pendingKey: String
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(displayNameKey, sourceFile.name)
            put(mimeTypeKey, mimeType)
            put(relativeDirKey, "$relativeDirValue/WormX")
            put(pendingKey, 1)
        }

        val itemUri = resolver.insert(collectionUri, values) ?: return
        resolver.openOutputStream(itemUri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        }
        values.clear()
        values.put(pendingKey, 0)
        resolver.update(itemUri, values, null, null)
    }

    private fun publishLegacyPreQ(context: Context, sourceFile: File) {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "WormX"
        )
        publicDir.mkdirs()
        val destFile = File(publicDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        // Make it show up immediately instead of waiting for the next media scan.
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(destFile.absolutePath), null, null
        )
    }
}
