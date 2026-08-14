package com.wormx.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wormx.app.ads.AdFrequencyManager
import com.wormx.app.databinding.ActivityMainBinding
import com.wormx.app.downloads.*
import com.wormx.app.sniffer.HeadlessVideoSniffer
import com.wormx.app.util.ClipboardLinkDetector
import com.wormx.app.vault.VaultPinActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DownloadRepository
    private lateinit var clipboardDetector: ClipboardLinkDetector
    private lateinit var adManager: AdFrequencyManager
    private lateinit var adapter: DownloadsAdapter
    private val resolverRegistry = QualityResolverRegistry()
    private val headlessSniffer by lazy { HeadlessVideoSniffer(this) }
    private var orbBusy = false

    private val uiScope = CoroutineScope(
        Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            android.widget.Toast.makeText(
                this,
                "Something went wrong: ${error.message ?: error.javaClass.simpleName}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DownloadRepository.getInstance(applicationContext)
        clipboardDetector = ClipboardLinkDetector(this)
        adManager = AdFrequencyManager(this).also { it.preload() }

        adapter = DownloadsAdapter(
            onPauseResume = ::togglePauseResume,
            onCancel = ::cancelDownload,
            onMoveToVault = ::moveToVault,
            onExtractAudio = ::extractAudio
        )
        binding.downloadsList.layoutManager = LinearLayoutManager(this)
        binding.downloadsList.adapter = adapter

        var lastKnownCompletedIds = emptySet<String>()
        repository.observe { items ->
            adapter.submitList(items)
            binding.emptyState.visibility =
                if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

            val completedNow = items.filter { it.state == DownloadState.COMPLETED }.map { it.id }.toSet()
            val newlyCompleted = completedNow - lastKnownCompletedIds
            lastKnownCompletedIds = completedNow
            if (newlyCompleted.isNotEmpty()) maybeShowAdAfterCompletion()
        }

        binding.pastePill.setOnClickListener { pasteAndDownload() }
        binding.orbWrap.setOnClickListener { pasteAndDownload() }
        binding.orbProgress.startIdlePulse()

        binding.pastePill.setOnLongClickListener {
            BatchPasteDialog.show(this) { urls -> BatchDownloadManager.queueAll(this, urls) }
            true
        }

        binding.homeNavButton.setOnClickListener { showPage(home = true) }
        binding.downloadsNavButton.setOnClickListener { showPage(home = false) }
        binding.vaultNavButton.setOnClickListener {
            startActivity(Intent(this, VaultPinActivity::class.java))
        }

        handleIncomingShare(intent)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun showPage(home: Boolean) {
        binding.homePage.visibility = if (home) android.view.View.VISIBLE else android.view.View.GONE
        binding.downloadsPage.visibility = if (home) android.view.View.GONE else android.view.View.VISIBLE
        binding.homeNavButton.setTextColor(resources.getColor(if (home) R.color.text_primary else R.color.text_faint, theme))
        binding.downloadsNavButton.setTextColor(resources.getColor(if (!home) R.color.text_primary else R.color.text_faint, theme))
    }

    override fun onResume() {
        super.onResume()
        clipboardDetector.currentClipboardLink()?.let {
            binding.pastePill.text = "Link found — tap to download"
        }
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedLink ->
                startDownload(sharedLink)
            }
        }
    }

    private fun pasteAndDownload() {
        val link = clipboardDetector.currentClipboardLink()
        if (link != null) {
            startDownload(link)
        } else {
            android.widget.Toast.makeText(
                this,
                "No link found on your clipboard. Copy a link first, then tap the orb.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Fetch flow, entirely chrome-free (no visible browser):
     *  1. Direct-file check (HEAD request)
     *  2. og:video meta-tag scan (plain GET + regex — works instantly on
     *     many sites without ever touching a WebView)
     *  3. Headless WebView sniff as a last resort, invisible to the user,
     *     with a short timeout
     * Whichever step succeeds first wins; if all three fail, the user gets
     * a clear "couldn't find a video" message instead of silence.
     */
    private fun startDownload(url: String) {
        if (orbBusy) return
        orbBusy = true
        // Deliberately NOT switching to the percentage ring here, and NOT
        // running any fixed-duration fake animation — there's no real
        // percentage to show yet at this point (a HEAD/GET request isn't
        // chunked progress). The idle swirl keeps spinning on its own, which
        // is honest continuous motion rather than a timed illusion, so the
        // orb never sits frozen or lies about how far along things are.
        binding.heroLabel.text = "Fetching link…"

        uiScope.launch {
            try {
                val options = resolverRegistry.resolve(url)
                if (options != null) {
                    finishFetch(options)
                    return@launch
                }

                // No direct file, no og:video tag — try the headless sniffer
                // before giving up. Still no browser window shown.
                headlessSniffer.sniff(url, binding.hiddenWebViewHost) { foundUrl ->
                    if (foundUrl != null) {
                        finishFetch(listOf(QualityOption(label = "Original", url = foundUrl, referer = url)))
                    } else {
                        resetOrb()
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Couldn't find a video on that link",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                resetOrb()
                android.widget.Toast.makeText(this@MainActivity, "Couldn't read that link", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishFetch(options: List<QualityOption>) {
        // We genuinely have an answer right now, so a real (not animated-
        // from-zero) 100% flash is an honest, instant confirmation rather
        // than padding for effect.
        binding.orbProgress.stopIdlePulse()
        binding.orbProgress.setProgress(100f)
        binding.orbPctText.text = "100%"
        binding.orbPctText.visibility = android.view.View.VISIBLE
        binding.orbIcon.alpha = 0.25f

        android.os.Handler(mainLooper).postDelayed({
            resetOrb()
            QualityPickerDialog.showIfNeeded(this@MainActivity, options) { chosen ->
                launchDownload(chosen.url, chosen.referer)
                showPage(home = false) // jump to Downloads so real progress is immediately visible
            }
        }, 200)
    }

    private fun resetOrb() {
        binding.orbPctText.visibility = android.view.View.GONE
        binding.orbIcon.alpha = 1f
        binding.orbProgress.startIdlePulse()
        binding.heroLabel.text = "Copy a link from any app — WormX grabs it instantly"
        orbBusy = false
    }

    private fun launchDownload(url: String, referer: String? = null) {
        val guessedName = url.substringAfterLast('/').substringBefore('?').ifBlank { "file_${System.currentTimeMillis()}" }
        val id = DownloadRepository.newId()

        val serviceIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_START
            putExtra(DownloadForegroundService.EXTRA_ITEM_ID, id)
            putExtra(DownloadForegroundService.EXTRA_URL, url)
            putExtra(DownloadForegroundService.EXTRA_FILENAME, guessedName)
            referer?.let { putExtra(DownloadForegroundService.EXTRA_REFERER, it) }
        }
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun togglePauseResume(item: DownloadItem) {
        val action = if (item.state == DownloadState.RUNNING)
            DownloadForegroundService.ACTION_PAUSE else DownloadForegroundService.ACTION_START
        val intent = Intent(this, DownloadForegroundService::class.java).apply {
            this.action = action
            putExtra(DownloadForegroundService.EXTRA_ITEM_ID, item.id)
            putExtra(DownloadForegroundService.EXTRA_URL, item.url)
            putExtra(DownloadForegroundService.EXTRA_FILENAME, item.fileName)
            item.referer?.let { putExtra(DownloadForegroundService.EXTRA_REFERER, it) }
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    private fun cancelDownload(item: DownloadItem) {
        val intent = Intent(this, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL
            putExtra(DownloadForegroundService.EXTRA_ITEM_ID, item.id)
        }
        startService(intent)
        repository.remove(item.id)
    }

    private fun moveToVault(item: DownloadItem) {
        com.wormx.app.vault.VaultRepository.getInstance(applicationContext).hideDownload(item)
        repository.remove(item.id)
    }

    private fun extractAudio(item: DownloadItem) {
        val source = item.destinationFile ?: return
        uiScope.launch(Dispatchers.IO) {
            MediaConverter.extractAudio(source) { success, output ->
                uiScope.launch {
                    val message = if (success) "Saved ${output?.name}" else "Audio extraction failed"
                    android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun maybeShowAdAfterCompletion() {
        if (adManager.onDownloadCompleted()) {
            adManager.showIfAllowed(this)
        }
    }
}
