package com.wormx.app.downloads

import android.app.AlertDialog
import android.content.Context

/**
 * Simple picker shown after a link resolves to more than one [QualityOption]
 * (e.g. several resolutions, or a "video" vs "audio only" choice).
 * With only one option it's skipped entirely and the download starts right away.
 */
object QualityPickerDialog {
    fun showIfNeeded(
        context: Context,
        options: List<QualityOption>,
        onPicked: (QualityOption) -> Unit
    ) {
        if (options.size <= 1) {
            options.firstOrNull()?.let(onPicked)
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
}
