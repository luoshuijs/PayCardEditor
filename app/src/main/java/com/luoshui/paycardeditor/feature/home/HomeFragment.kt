package com.luoshui.paycardeditor.feature.home

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.App
import com.luoshui.paycardeditor.app.MainActivity
import com.luoshui.paycardeditor.core.MiPayNavigator
import com.luoshui.paycardeditor.data.ModuleStateRepository
import com.luoshui.paycardeditor.feature.troubleshoot.TroubleshootActivity
import com.luoshui.paycardeditor.model.CardSnapshotFormatter
import com.luoshui.paycardeditor.model.ModuleStatusLevel
import com.luoshui.paycardeditor.model.ModuleStatusState
import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
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
        binding.buttonSyncSnapshot.setOnClickListener {
            renderState()
            (activity as? MainActivity)?.showCardSnapshotDialog()
        }
        binding.buttonOpenMipay.setOnClickListener { MiPayNavigator.open(requireContext()) }
        binding.buttonTroubleshoot.setOnClickListener {
            startActivity(Intent(requireContext(), TroubleshootActivity::class.java))
        }
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

    /**
     * Maps the module status level to a pair of M3 theme attributes:
     *  ACTIVE   → primary  / primary-container
     *  WAITING  → secondary / secondary-container
     *  INACTIVE → error    / error-container
     *
     * Reading the colors via [MaterialColors.getColor] keeps everything in
     * sync with dynamic color and dark mode.
     */
    private fun renderModuleStatus(status: ModuleStatusState) {
        binding.textActivationTitle.text = status.title
        binding.textActivationDetail.text = status.detail

        val card = binding.cardActivation
        val accentAttr: Int
        val containerAttr: Int
        when (status.level) {
            ModuleStatusLevel.ACTIVE -> {
                accentAttr = androidx.appcompat.R.attr.colorPrimary
                containerAttr = com.google.android.material.R.attr.colorPrimaryContainer
            }
            ModuleStatusLevel.WAITING -> {
                accentAttr = com.google.android.material.R.attr.colorSecondary
                containerAttr = com.google.android.material.R.attr.colorSecondaryContainer
            }
            ModuleStatusLevel.INACTIVE -> {
                accentAttr = androidx.appcompat.R.attr.colorError
                containerAttr = com.google.android.material.R.attr.colorErrorContainer
            }
        }
        val accent = MaterialColors.getColor(card, accentAttr)
        val container = MaterialColors.getColor(card, containerAttr)

        binding.viewActivationIndicator.backgroundTintList = ColorStateList.valueOf(accent)
        card.setCardBackgroundColor(container)
        card.strokeColor = accent
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "home"
    }
}
