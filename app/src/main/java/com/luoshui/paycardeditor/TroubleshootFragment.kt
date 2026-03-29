package com.luoshui.paycardeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.luoshui.paycardeditor.databinding.FragmentTroubleshootBinding

class TroubleshootFragment : Fragment() {

    private var _binding: FragmentTroubleshootBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTroubleshootBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonRefresh.setOnClickListener { renderState() }
        binding.buttonCopyDebug.setOnClickListener {
            copyText(getString(R.string.troubleshoot_debug_title), binding.textDebugInfo.text)
        }
        binding.buttonCopyHooks.setOnClickListener {
            copyText(getString(R.string.troubleshoot_hook_list_title), binding.textHookMethods.text)
        }
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun renderState() {
        val state = ModuleStateRepository.loadTroubleshootState()
        binding.textDebugInfo.text = state.debugInfo
        binding.textUpdatedAt.text = getString(
            R.string.troubleshoot_updated_at,
            CardSnapshotFormatter.formatTimestamp(state.updatedAt),
        )
        binding.textHookMethods.text = buildHookMethodSpans(state.hookMethods)
    }

    private fun buildHookMethodSpans(text: String): CharSequence {
        if (text.isBlank()) {
            return text
        }
        val resolvedColor = ContextCompat.getColor(requireContext(), R.color.tone_jade)
        val missingColor = ContextCompat.getColor(requireContext(), R.color.tone_coral)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val builder = SpannableStringBuilder()
        val blocks = text.split("\n\n")
        blocks.forEachIndexed { blockIndex, block ->
            if (blockIndex > 0) {
                builder.append("\n\n")
            }
            val start = builder.length
            builder.append(block)
            val isMissing = block.lineSequence().lastOrNull()?.trim() == "= (void*)0"
            val lines = block.lines()
            if (lines.isNotEmpty()) {
                builder.setSpan(
                    ForegroundColorSpan(if (isMissing) missingColor else resolvedColor),
                    start,
                    start + lines.first().length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (lines.size >= 2) {
                val ownerStart = start + lines.first().length + 1
                val ownerEnd = ownerStart + lines[1].length
                builder.setSpan(
                    ForegroundColorSpan(secondaryColor),
                    ownerStart,
                    ownerEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            val statusLine = lines.lastOrNull().orEmpty()
            val statusStart = start + block.length - statusLine.length
            builder.setSpan(
                ForegroundColorSpan(if (isMissing) missingColor else resolvedColor),
                statusStart,
                start + block.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return builder
    }

    private fun copyText(label: String, text: CharSequence) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), R.string.troubleshoot_copy_done, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
