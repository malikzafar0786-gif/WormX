package com.wormx.app.sniffer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wormx.app.databinding.ItemSniffedVideoBinding

class SniffedVideoAdapter(
    private val onPick: (SniffedVideo) -> Unit
) : RecyclerView.Adapter<SniffedVideoAdapter.ViewHolder>() {

    private var items: List<SniffedVideo> = emptyList()

    fun submitList(newItems: List<SniffedVideo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSniffedVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.videoLabel.text = item.label
        holder.binding.videoUrlPreview.text = item.url.takeLast(48)
        holder.binding.streamBadge.visibility =
            if (item.isStream) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.root.setOnClickListener { onPick(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val binding: ItemSniffedVideoBinding) : RecyclerView.ViewHolder(binding.root)
}
