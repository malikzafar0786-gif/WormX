package com.wormx.app.downloads

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Two behaviours depending on what the resolver found:
 *  - A single plain option (no title — a direct file or og:video link) skips
 *    any dialog entirely and downloads immediately, preserving the
 *    "no browser, just starts" feel for the common case.
 *  - A single *rich* option (has a title — came from the FastAPI/yt-dlp
 *    backend) shows a preview: thumbnail, title, and a Download button, so
 *    the person can confirm what's about to download before it starts.
 *  - Multiple options falls back to a simple quality list.
 */
object QualityPickerDialog {

    private val thumbnailClient = OkHttpClient()

    fun showIfNeeded(
        context: Context,
        options: List<QualityOption>,
        onPicked: (QualityOption) -> Unit
    ) {
        if (options.isEmpty()) return

        if (options.size == 1) {
            val only = options[0]
            if (only.title != null) {
                showPreview(context, only, onPicked)
            } else {
                onPicked(only)
            }
            return
        }

        val labels = options.map { opt ->
            val size = if (opt.approxSizeBytes > 0) " (${opt.approxSizeBytes / 1_000_000} MB)" else ""
            opt.label + size
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Choose quality")
            .setItems(labels) { _, which -> onPicked(options[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPreview(context: Context, option: QualityOption, onPicked: (QualityOption) -> Unit) {
        val view = android.view.LayoutInflater.from(context)
            .inflate(com.wormx.app.R.layout.dialog_extraction_preview, null)

        val thumbnailView = view.findViewById<ImageView>(com.wormx.app.R.id.previewThumbnail)
        val titleView = view.findViewById<android.widget.TextView>(com.wormx.app.R.id.previewTitle)
        val downloadButton = view.findViewById<android.widget.TextView>(com.wormx.app.R.id.previewDownload)
        val cancelButton = view.findViewById<android.widget.TextView>(com.wormx.app.R.id.previewCancel)

        titleView.text = option.title ?: "Video"

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        downloadButton.setOnClickListener {
            onPicked(option)
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.show()

        option.thumbnailUrl?.let { thumbUrl -> loadThumbnail(thumbUrl, thumbnailView) }
    }

    /**
     * Manual background fetch + decode instead of pulling in an image-loading
     * library (Coil/Glide) for just this one preview thumbnail — keeps the
     * dependency surface small after the earlier lesson about an external
     * media library (ffmpeg-kit) quietly disappearing from Maven and
     * breaking the build.
     */
    private fun loadThumbnail(url: String, target: ImageView) {
        Thread {
            try {
                val request = Request.Builder().url(url).build()
                thumbnailClient.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes() ?: return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    Handler(Looper.getMainLooper()).post {
                        target.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // Thumbnail is cosmetic — a failed load just leaves the
                // placeholder background, nothing to surface to the user.
            }
        }.start()
    }
}
