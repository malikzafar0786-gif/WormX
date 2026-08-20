package com.wormx.app.vault

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wormx.app.databinding.ItemVaultBinding

class VaultAdapter(
    private val onOpen: (VaultEntry) -> Unit,
    private val onRemoveFromVault: (VaultEntry) -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    private var items: List<VaultEntry> = emptyList()

    fun submitList(newItems: List<VaultEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val binding = ItemVaultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VaultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val entry = items[position]
        holder.bind(entry, onOpen, onRemoveFromVault)
    }

    override fun getItemCount() = items.size

    class VaultViewHolder(private val binding: ItemVaultBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: VaultEntry, onOpen: (VaultEntry) -> Unit, onRemove: (VaultEntry) -> Unit) {
            binding.fileLabel.text = entry.originalCategory
            binding.root.setOnClickListener { onOpen(entry) }
            binding.root.setOnLongClickListener { onRemove(entry); true }
        }
    }
}
