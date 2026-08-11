package com.wormx.app.util

import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns

/**
 * Watches the clipboard and surfaces a "Link detected" prompt so the user
 * can start a download with one tap, without pasting manually.
 */
class ClipboardLinkDetector(context: Context) {

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Call when the app resumes / becomes visible to check for a fresh link. */
    fun currentClipboardLink(): String? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(null)?.toString()?.trim() ?: return null
        return if (Patterns.WEB_URL.matcher(text).matches()) text else null
    }

    fun onClipboardChanged(callback: (String?) -> Unit) {
        clipboardManager.addPrimaryClipChangedListener {
            callback(currentClipboardLink())
        }
    }
}
