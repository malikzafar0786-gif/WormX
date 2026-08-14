package com.wormx.app.sniffer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wormx.app.databinding.ActivityVideoSnifferBinding
import java.util.concurrent.CopyOnWriteArrayList

data class SniffedVideo(
    val url: String,
    val label: String,
    val isStream: Boolean // .m3u8/.mpd — segmented, needs a merge step before it's a single file
)

/**
 * The core of "download from any site": instead of writing a separate
 * extractor per platform, load the page in a real WebView and watch every
 * network request the page itself makes. Any request whose URL looks like a
 * video (by extension, since shouldInterceptRequest doesn't expose response
 * headers without a second round-trip) gets surfaced to the user to pick
 * from — the same technique the reference app uses.
 *
 * This sees exactly what the phone's own browser would request, so it works
 * on unfamiliar sites without platform-specific code. It won't defeat DRM
 * or truly private/authenticated streams, and .m3u8/.mpd results are
 * playlists (multiple segments) rather than a single file — see the
 * isStream flag.
 */
class VideoSnifferActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoSnifferBinding
    private val found = CopyOnWriteArrayList<SniffedVideo>()
    private lateinit var adapter: SniffedVideoAdapter

    companion object {
        const val EXTRA_START_URL = "extra_start_url"
        const val EXTRA_RESULT_VIDEO_URL = "extra_result_video_url"

        private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mov", ".mkv", ".3gp")
        private val STREAM_EXTENSIONS = listOf(".m3u8", ".mpd")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoSnifferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SniffedVideoAdapter { video -> pickVideo(video) }
        binding.foundList.layoutManager = LinearLayoutManager(this)
        binding.foundList.adapter = adapter

        binding.closeButton.setOnClickListener { finish() }

        setupWebView()

        val startUrl = intent.getStringExtra(EXTRA_START_URL)
        if (!startUrl.isNullOrBlank()) {
            binding.webView.loadUrl(startUrl)
        }
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.settings.mediaPlaybackRequiresUserGesture = false

        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressLabel.text = "Loading page…"
                binding.progressLabel.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressLabel.visibility = android.view.View.GONE
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                if (url != null) considerUrl(url)
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun considerUrl(url: String) {
        val lower = url.lowercase().substringBefore('?')

        val isVideo = VIDEO_EXTENSIONS.any { lower.endsWith(it) }
        val isStream = STREAM_EXTENSIONS.any { lower.endsWith(it) }
        if (!isVideo && !isStream) return

        if (found.any { it.url == url }) return // already have this exact one

        val label = guessLabel(url, isStream)
        val video = SniffedVideo(url, label, isStream)
        found.add(video)

        runOnUiThread {
            adapter.submitList(found.toList())
            binding.foundPanel.visibility = android.view.View.VISIBLE
            binding.foundCount.text = "${found.size} video${if (found.size == 1) "" else "s"} found on this page"
        }
    }

    private fun guessLabel(url: String, isStream: Boolean): String {
        val resolutionMatch = Regex("(240|360|480|540|720|1080|1440|2160)p?").find(url)
        val quality = resolutionMatch?.value?.let { if (it.endsWith("p")) it else "${it}p" }
        return when {
            quality != null -> quality
            isStream -> "Stream (HLS/DASH)"
            else -> "Video file"
        }
    }

    private fun pickVideo(video: SniffedVideo) {
        if (video.isStream) {
            android.widget.Toast.makeText(
                this,
                "This is a segmented stream (HLS/DASH) — single-file download isn't supported yet.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val result = Intent().putExtra(EXTRA_RESULT_VIDEO_URL, video.url)
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
