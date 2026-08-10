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
    private val uiScope = CoroutineScope(Dispatchers.Main)

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
            onConvertToMp3 = ::convertToMp3
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
        binding.vaultNavButton.setOnClickListener {
            startActivity(Intent(this, VaultPinActivity::class.java))
        }
        // Long-press the Grab pill to queue several links at once.
        binding.grabFab.setOnLongClickListener {
            BatchPasteDialog.show(this) { urls -> BatchDownloadManager.queueAll(this, urls) }
            true
        }

        handleIncomingShare(intent)
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
        if (link != null) startDownload(link)
    }

    private fun startDownload(url: String) {
        // Resolve first so a link with multiple qualities (or an audio-only
        // option) shows a picker instead of silently grabbing the default.
        uiScope.launch {
            val options = resolverRegistry.resolve(url)
            QualityPickerDialog.showIfNeeded(this@MainActivity, options) { chosen ->
                launchDownload(chosen.url)
            }
        }
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

    private fun convertToMp3(item: DownloadItem) {
        val source = item.destinationFile ?: return
        uiScope.launch(Dispatchers.IO) {
            MediaConverter.extractAudioToMp3(source) { success, output ->
                uiScope.launch {
                    val message = if (success) "Saved ${output?.name} to Music" else "Conversion failed"
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
