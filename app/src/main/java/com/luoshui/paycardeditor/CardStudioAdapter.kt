package com.luoshui.paycardeditor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luoshui.paycardeditor.databinding.ItemCardAssetBinding
import java.io.File

class CardStudioAdapter(
    private val onClick: (CardAsset) -> Unit,
    private val onLongClick: (CardAsset) -> Boolean,
) : ListAdapter<CardAsset, CardStudioAdapter.AssetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemCardAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetViewHolder(
        private val binding: ItemCardAssetBinding,
        private val onClick: (CardAsset) -> Unit,
        private val onLongClick: (CardAsset) -> Boolean,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(asset: CardAsset) {
            binding.textTitle.text = asset.displayName
            binding.textSubtitle.text = binding.root.context.getString(
                R.string.asset_item_subtitle,
                BankCardRuleRepository.assignmentCount(asset),
            )
            val bitmap = CardImageProcessor.decodePreview(File(asset.absolutePath))
            if (bitmap != null) {
                binding.imagePreview.setImageDrawable(bitmap.toDrawable(binding.root.resources))
            } else {
                binding.imagePreview.setImageResource(android.R.drawable.ic_menu_report_image)
            }
            binding.root.setOnClickListener { onClick(asset) }
            binding.root.setOnLongClickListener { onLongClick(asset) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CardAsset>() {
            override fun areItemsTheSame(oldItem: CardAsset, newItem: CardAsset): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CardAsset, newItem: CardAsset): Boolean = oldItem == newItem
        }
    }
}
