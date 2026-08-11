package com.wormx.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wormx.app.ads.AdFrequencyManager
import com.wormx.app.databinding.ActivityMainBinding
import com.wormx.app.downloads.*
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
    private var orbBusy = false

    private val snifferLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val videoUrl = result.data?.getStringExtra(
                com.wormx.app.sniffer.VideoSnifferActivity.EXTRA_RESULT_VIDEO_URL
            )
            if (videoUrl != null) launchDownload(videoUrl)
        }
        resetOrb()
    }
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

            // Fire the (rate-limited) ad check whenever a new item finishes.
            val completedNow = items.filter { it.state == DownloadState.COMPLETED }.map { it.id }.toSet()
            val newlyCompleted = completedNow - lastKnownCompletedIds
            lastKnownCompletedIds = completedNow
            if (newlyCompleted.isNotEmpty()) maybeShowAdAfterCompletion()
        }

        binding.pastePill.setOnClickListener { pasteAndDownload() }
        binding.grabFab.setOnClickListener { pasteAndDownload() }
        binding.orbWrap.setOnClickListener { pasteAndDownload() }
        binding.orbProgress.startIdlePulse()
        binding.vaultNavButton.setOnClickListener {
            startActivity(Intent(this, VaultPinActivity::class.java))
        }
        // Long-press the Grab pill to queue several links at once.
        binding.grabFab.setOnLongClickListener {
            BatchPasteDialog.show(this) { urls -> BatchDownloadManager.queueAll(this, urls) }
            true
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

    override fun onResume() {
        super.onResume()
        // Surface a one-tap prompt if a link is already sitting on the clipboard
        clipboardDetector.currentClipboardLink()?.let { link ->
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
                "No link found on your clipboard. Copy a link first, then tap Grab.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startDownload(url: String) {
        if (orbBusy) return
        orbBusy = true
        binding.orbProgress.stopIdlePulse()
        binding.orbPctText.visibility = android.view.View.VISIBLE
        binding.orbIcon.alpha = 0.25f
        binding.heroLabel.text = "Fetching link…"

        // Drive the ring smoothly while the real (usually near-instant) HEAD
        // request runs in parallel — gives the same two-phase feel as the
        // prototype instead of a dead tap-and-wait.
        val fetchAnimator = android.animation.ValueAnimator.ofInt(0, 92).apply {
            duration = 900
            addUpdateListener {
                val pct = it.animatedValue as Int
                binding.orbProgress.setProgress(pct.toFloat())
                binding.orbPctText.text = "$pct%"
            }
        }
        fetchAnimator.start()

        uiScope.launch {
            try {
                val options = resolverRegistry.resolve(url)
                fetchAnimator.cancel()

                if (options == null) {
                    // Not a direct file — open the page in the sniffer so the
                    // user can browse it and grab whatever video it plays,
                    // on any site, the same way the reference app does.
                    resetOrb()
                    val intent = Intent(this@MainActivity, com.wormx.app.sniffer.VideoSnifferActivity::class.java)
                        .putExtra(com.wormx.app.sniffer.VideoSnifferActivity.EXTRA_START_URL, url)
                    snifferLauncher.launch(intent)
                    return@launch
                }

                binding.orbProgress.animateProgress(100f, 150)
                binding.orbPctText.text = "100%"

                android.os.Handler(mainLooper).postDelayed({
                    resetOrb()
                    QualityPickerDialog.showIfNeeded(this@MainActivity, options) { chosen ->
                        launchDownload(chosen.url)
                    }
                }, 250)
            } catch (e: Exception) {
                fetchAnimator.cancel()
                resetOrb()
                android.widget.Toast.makeText(this@MainActivity, "Couldn't read that link", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetOrb() {
        binding.orbPctText.visibility = android.view.View.GONE
        binding.orbIcon.alpha = 1f
        binding.orbProgress.setProgress(0f)
        binding.orbProgress.startIdlePulse()
        binding.heroLabel.text = "Copy a link from any app — WormX grabs it instantly"
        orbBusy = false
    }

    private fun launchDownload(url: String) {
        val guessedName = url.substringAfterLast('/').ifBlank { "file_${System.currentTimeMillis()}" }
        val id = DownloadRepository.newId()

        val serviceIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_START
            putExtra(DownloadForegroundService.EXTRA_ITEM_ID, id)
            putExtra(DownloadForegroundService.EXTRA_URL, url)
            putExtra(DownloadForegroundService.EXTRA_FILENAME, guessedName)
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

    /** Called whenever a download finishes; respects the daily ad cap. */
    private fun maybeShowAdAfterCompletion() {
        if (adManager.onDownloadCompleted()) {
            adManager.showIfAllowed(this)
        }
    }
}
