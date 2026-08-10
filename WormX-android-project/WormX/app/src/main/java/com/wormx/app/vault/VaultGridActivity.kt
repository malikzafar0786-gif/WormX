package com.wormx.app.vault

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.wormx.app.databinding.ActivityVaultGridBinding

class VaultGridActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultGridBinding
    private lateinit var repository: VaultRepository
    private var isDecoyMode = false

    companion object {
        const val EXTRA_DECOY_MODE = "extra_decoy_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultGridBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isDecoyMode = intent.getBooleanExtra(EXTRA_DECOY_MODE, false)
        repository = VaultRepository.getInstance(applicationContext)

        binding.vaultRecycler.layoutManager = GridLayoutManager(this, 3)
        val adapter = VaultAdapter(
            onOpen = { /* decrypt to a temp file and open with an appropriate viewer */ },
            onRemoveFromVault = { entry -> repository.restoreToDownloads(entry) }
        )
        binding.vaultRecycler.adapter = adapter

        if (isDecoyMode) {
            // Decoy PIN was used: always render an empty vault, regardless of
            // what's actually stored, and never touch the real repository.
            adapter.submitList(emptyList())
            binding.emptyState.visibility = android.view.View.VISIBLE
            binding.itemCount.text = "0 items hidden from your gallery"
        } else {
            repository.observe { entries ->
                adapter.submitList(entries)
                binding.emptyState.visibility =
                    if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.itemCount.text = "${entries.size} items hidden from your gallery"
            }
        }

        binding.backButton.setOnClickListener { finish() }
    }
}
