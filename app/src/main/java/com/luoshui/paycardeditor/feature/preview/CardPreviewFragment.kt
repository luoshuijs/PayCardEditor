package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.MainActivity
import com.luoshui.paycardeditor.data.BankCardRuleRepository
import com.luoshui.paycardeditor.data.ModuleStateRepository
import com.luoshui.paycardeditor.model.CardSnapshot


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luoshui.paycardeditor.databinding.FragmentCardPreviewBinding

class CardPreviewFragment : Fragment() {

    private var _binding: FragmentCardPreviewBinding? = null
    private val binding get() = _binding!!
    private val adapter = CardPreviewAdapter(::showCardActions)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPreview.adapter = adapter
        binding.buttonRefreshPreview.setOnClickListener { refreshCards() }
        refreshCards()
    }

    override fun onResume() {
        super.onResume()
        refreshCards()
    }

    private fun refreshCards() {
        val state = ModuleStateRepository.loadHomeState()
        val cards = state.cardState.cards
        adapter.submitList(cards)
        binding.recyclerPreview.isVisible = cards.isNotEmpty()
        binding.layoutPreviewEmpty.isVisible = cards.isEmpty()
        binding.textPreviewCount.text = getString(R.string.preview_count_format, cards.size)
        binding.textPreviewWarning.isVisible = state.cardState.warning.isNotBlank()
        binding.textPreviewWarning.text = state.cardState.warning
    }

    private fun showCardActions(snapshot: CardSnapshot) {
        val appliedRule = BankCardRuleRepository.findRule(snapshot)
        val message = buildString {
            appendLine(snapshot.title)
            appendLine()
            appendLine(getString(R.string.preview_detail_type, snapshot.categoryLabel))
            appendLine(getString(R.string.preview_detail_identifier, snapshot.secondaryLabel))
            append(getString(R.string.preview_detail_status, if (appliedRule == null) getString(R.string.rule_default_short) else getString(R.string.rule_applied_short)))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.preview_dialog_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
        if (appliedRule != null) {
            dialog.setPositiveButton(R.string.preview_restore_action) { _, _ ->
                BankCardRuleRepository.removeRule(snapshot)
                refreshCards()
                (activity as? MainActivity)?.showMessage(getString(R.string.rule_restored_message, snapshot.title))
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "preview"
    }
}
