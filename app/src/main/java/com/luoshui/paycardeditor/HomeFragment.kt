package com.luoshui.paycardeditor

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luoshui.paycardeditor.databinding.FragmentHomeBinding
import io.github.libxposed.service.XposedService

class HomeFragment : Fragment(), App.ServiceStateListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonActivationInfo.setOnClickListener { showActivationDialog() }
        binding.buttonSyncSnapshot.setOnClickListener {
            renderState()
            (activity as? MainActivity)?.showCardSnapshotDialog()
        }
        binding.buttonOpenMipay.setOnClickListener { MiPayNavigator.open(requireContext()) }
        renderState()
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, notifyImmediately = true)
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        activity?.runOnUiThread {
            if (_binding != null) {
                renderState()
            }
        }
    }

    private fun renderState() {
        val state = ModuleStateRepository.loadHomeState()
        renderModuleStatus(state.moduleStatus)
        binding.textCardCount.text = getString(R.string.snapshot_count_format, state.cardState.cards.size)
        binding.textLastUpdated.text = getString(
            R.string.last_updated_format,
            CardSnapshotFormatter.formatTimestamp(state.cardState.lastUpdated),
        )
        binding.textLastSource.text = getString(
            R.string.last_source_format,
            state.cardState.lastSource.ifBlank { getString(R.string.none_label) },
        )
        binding.textWarning.isVisible = state.cardState.warning.isNotBlank()
        binding.textWarning.text = state.cardState.warning
    }

    private fun renderModuleStatus(status: ModuleStatusState) {
        binding.textActivationTitle.text = status.title
        binding.textActivationDetail.text = status.detail
        val (indicator, container, stroke) = when (status.level) {
            ModuleStatusLevel.ACTIVE -> Triple(R.color.tone_jade, R.color.tone_jade_soft, R.color.tone_jade)
            ModuleStatusLevel.WAITING -> Triple(R.color.tone_amber, R.color.tone_amber_soft, R.color.tone_amber)
            ModuleStatusLevel.INACTIVE -> Triple(R.color.tone_coral, R.color.tone_coral_soft, R.color.tone_coral)
        }
        binding.viewActivationIndicator.backgroundTintList = ColorStateList.valueOf(color(indicator))
        binding.cardActivation.setCardBackgroundColor(color(container))
        binding.cardActivation.strokeColor = color(stroke)
    }

    private fun showActivationDialog() {
        val status = ModuleStateRepository.loadHomeState().moduleStatus
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.home_status_label)
            .setMessage("${status.title}\n\n${status.detail}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(requireContext(), colorRes)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "home"
    }
}
