package com.wormx.app.downloads

import android.content.Context

/**
 * Holds the FastAPI extractor server's base URL. Editable from within the
 * app (long-press the "WormX" title on Home) so switching networks/servers
 * never requires a rebuild — this address changes constantly during
 * development (emulator vs real device vs LAN vs a public tunnel), and
 * every prior round trip in this project cost a full GitHub Actions build.
 */
object ServerConfig {
    private const val PREFS = "wormx_server_prefs"
    private const val KEY_BASE_URL = "extractor_base_url"

    // Now pointing at the live Render deployment. Falls back to this if the
    // person hasn't overridden it via the in-app "long-press WormX title"
    // settings dialog. Render's free tier sleeps after ~15 min of no
    // traffic, so the very first request after a while can take 30-40s to
    // wake it back up — the resolver chain's other fallbacks (direct-file,
    // og:video) still work in the meantime since this resolver fails
    // gracefully on timeout.
    private const val DEFAULT_BASE_URL = "https://wormx-backend.onrender.com"

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, url.trim().trimEnd('/'))
            .apply()
    }
}
