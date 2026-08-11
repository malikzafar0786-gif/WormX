package com.wormx.app.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wormx.app.databinding.ItemDownloadBinding

class DownloadsAdapter(
    private val onPauseResume: (DownloadItem) -> Unit,
    private val onCancel: (DownloadItem) -> Unit,
    private val onMoveToVault: (DownloadItem) -> Unit,
    private val onExtractAudio: (DownloadItem) -> Unit = {}
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    private var items: List<DownloadItem> = emptyList()

    fun submitList(newItems: List<DownloadItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onPauseResume, onCancel, onMoveToVault, onExtractAudio)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: DownloadItem,
            onPauseResume: (DownloadItem) -> Unit,
            onCancel: (DownloadItem) -> Unit,
            onMoveToVault: (DownloadItem) -> Unit,
            onExtractAudio: (DownloadItem) -> Unit
        ) {
            binding.fileName.text = item.fileName
            binding.progressBar.progress = item.progressPercent
            binding.statusText.text = "${item.progressPercent}% • ${item.state}"
            binding.pauseResumeButton.text = if (item.state == DownloadState.RUNNING) "Pause" else "Resume"

            binding.pauseResumeButton.setOnClickListener { onPauseResume(item) }
            binding.cancelButton.setOnClickListener { onCancel(item) }
            binding.vaultButton.setOnClickListener { onMoveToVault(item) }

            // Completed videos: long-press the name to extract audio as MP3.
            val canConvert = item.state == DownloadState.COMPLETED && item.fileCategory == FileCategory.VIDEO
            binding.fileName.setOnLongClickListener {
                if (canConvert) onExtractAudio(item)
                canConvert
            }
        }
    }
}
