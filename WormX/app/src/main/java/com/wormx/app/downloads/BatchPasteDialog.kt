package com.wormx.app.downloads

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText

object BatchPasteDialog {
    fun show(context: Context, onQueue: (List<String>) -> Unit) {
        val input = EditText(context).apply {
            hint = "Paste one link per line"
            minLines = 5
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(context)
            .setTitle("Bulk download")
            .setView(input)
            .setPositiveButton("Queue all") { _, _ ->
                val urls = BatchDownloadManager.extractUrls(input.text.toString())
                onQueue(urls)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
