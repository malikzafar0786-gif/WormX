package com.wormx.app.sniffer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Runs the same network-level video detection as [VideoSnifferActivity], but
 * with the WebView attached invisibly (1x1dp, alpha 0) instead of shown to
 * the user — this is the "no browser opens, download just starts" behaviour
 * the reference app has. It's a background step inside the fetch phase, not
 * a separate screen.
 *
 * Trade-off: because there's no visible page for the user to interact with,
 * this only catches videos that a page requests automatically (autoplay,
 * preloaded players, or plain <video src=...> tags) within [timeoutMs] —
 * sites that only fetch video after a manual tap on their own Play button
 * won't be caught here. For those, a visible-browser mode would be needed;
 * this class intentionally trades that coverage for the fast, chrome-free
 * experience that was asked for.
 */
class HeadlessVideoSniffer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mov", ".mkv", ".3gp")
        private const val DEFAULT_TIMEOUT_MS = 7000L
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun sniff(
        url: String,
        attachRoot: ViewGroup,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onResult: (String?) -> Unit
    ) {
        var settled = false
        val webView = WebView(context)

        fun finish(result: String?) {
            if (settled) return
            settled = true
            mainHandler.post {
                onResult(result)
                webView.stopLoading()
                webView.destroy()
                (webView.parent as? ViewGroup)?.removeView(webView)
            }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null) {
                    val lower = reqUrl.lowercase().substringBefore('?')
                    if (VIDEO_EXTENSIONS.any { lower.endsWith(it) }) {
                        finish(reqUrl)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Never let the hidden WebView navigate to a non-http(s)
                // scheme (app deep links, custom schemes) — with no visible
                // UI there'd be nothing to show an error on anyway, so just
                // swallow it and keep waiting for the timeout.
                val scheme = request?.url?.scheme
                return scheme != "http" && scheme != "https"
            }
        }

        // Keep it attached to a real window (required for network/JS to run)
        // but invisible and effectively zero-sized.
        webView.layoutParams = ViewGroup.LayoutParams(1, 1)
        webView.alpha = 0f
        attachRoot.addView(webView)

        mainHandler.postDelayed({ finish(null) }, timeoutMs)

        mainHandler.post {
            webView.loadUrl(url)
        }
    }
}
