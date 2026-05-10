package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.BankCardRuleRepository
import com.luoshui.paycardeditor.model.CardSnapshot


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.luoshui.paycardeditor.databinding.ItemCardPreviewBinding

class CardPreviewAdapter(
    private val onClick: (CardSnapshot) -> Unit,
) : ListAdapter<CardSnapshot, CardPreviewAdapter.PreviewViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val binding = ItemCardPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PreviewViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PreviewViewHolder(
        private val binding: ItemCardPreviewBinding,
        private val onClick: (CardSnapshot) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(snapshot: CardSnapshot) {
            val rule = BankCardRuleRepository.findRule(snapshot)
            binding.textTitle.text = snapshot.title
            binding.textSubtitle.text = binding.root.context.getString(
                R.string.preview_item_subtitle,
                snapshot.categoryLabel,
                snapshot.secondaryLabel,
            )
            binding.chipStatus.text = binding.root.context.getString(
                if (rule == null) R.string.rule_default_short else R.string.rule_applied_short
            )
            // Color the chip with on-tertiary-container when a custom rule is
            // applied, on-surface-variant otherwise. Pulls colors from the
            // theme so dynamic color and dark mode keep working.
            val container = com.google.android.material.color.MaterialColors.getColor(
                binding.chipStatus,
                if (rule == null) com.google.android.material.R.attr.colorSurfaceContainerHigh
                else com.google.android.material.R.attr.colorTertiaryContainer,
            )
            val onContainer = com.google.android.material.color.MaterialColors.getColor(
                binding.chipStatus,
                if (rule == null) com.google.android.material.R.attr.colorOnSurfaceVariant
                else com.google.android.material.R.attr.colorOnTertiaryContainer,
            )
            binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(container)
            binding.chipStatus.setTextColor(onContainer)
            val imageModel = CardPreviewImageResolver.resolve(snapshot, rule?.replaceCardArt.orEmpty())
            if (imageModel != null) {
                Glide.with(binding.imagePreview)
                    .load(imageModel)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(binding.imagePreview)
            } else {
                Glide.with(binding.imagePreview).clear(binding.imagePreview)
                binding.imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            binding.root.setOnClickListener { onClick(snapshot) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CardSnapshot>() {
            override fun areItemsTheSame(oldItem: CardSnapshot, newItem: CardSnapshot): Boolean = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: CardSnapshot, newItem: CardSnapshot): Boolean = oldItem == newItem
        }
    }
}
