package com.luoshui.paycardeditor.feature.studio

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.MainActivity
import com.luoshui.paycardeditor.data.BankCardRuleRepository
import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.data.CardAssetRepository
import com.luoshui.paycardeditor.data.ModuleStateRepository


import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.luoshui.paycardeditor.databinding.FragmentCardStudioBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class CardStudioFragment : Fragment() {

    private var _binding: FragmentCardStudioBinding? = null
    private val binding get() = _binding!!
    private val adapter = CardStudioAdapter(
        onClick = ::showApplyDialog,
        onLongClick = ::showAssetActions,
    )

    private var pendingCrop: PendingCrop? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            startCrop(result.data!!.data!!, pendingCrop ?: PendingCrop())
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = pendingCrop ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            pendingCrop = null
            return@registerForActivityResult
        }
        val outputUri = UCrop.getOutput(result.data ?: return@registerForActivityResult)
        val outputFile = File(outputUri?.path ?: request.outputFile.absolutePath)
        val roundedFile = CardAssetRepository.createTempFile("rounded_")
        CardImageProcessor.makeRoundedPng(outputFile, roundedFile)
        val asset = CardAssetRepository.saveAsset(
            sourceFile = roundedFile,
            displayName = request.displayName,
            existingAssetId = request.assetId,
        )
        pendingCrop = null
        refreshAssets()
        (activity as? MainActivity)?.showMessage(
            getString(if (request.assetId == null) R.string.asset_saved_message else R.string.asset_updated_message, asset.displayName)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerAssets.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAssets.adapter = adapter
        binding.buttonAddAsset.setOnClickListener { pickNewAsset() }
        binding.buttonEmptyAdd.setOnClickListener { pickNewAsset() }
        refreshAssets()
    }

    override fun onResume() {
        super.onResume()
        refreshAssets()
    }

    private fun refreshAssets() {
        val assets = CardAssetRepository.listAssets()
        adapter.submitList(assets)
        binding.recyclerAssets.isVisible = assets.isNotEmpty()
        binding.layoutEmpty.isVisible = assets.isEmpty()
        binding.textAssetCount.text = getString(R.string.asset_count_format, assets.size)
        binding.textAssignmentHint.text = getString(R.string.asset_assignment_hint, assets.sumOf { BankCardRuleRepository.assignmentCount(it) })
    }

    private fun pickNewAsset() {
        pendingCrop = PendingCrop(assetId = null, displayName = getString(R.string.asset_default_name))
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        imagePicker.launch(intent)
    }

    private fun showApplyDialog(asset: CardAsset) {
        val supportedCards = ModuleStateRepository.loadHomeState().cardState.cards.filter { it.supportsCustomCardArt }
        if (supportedCards.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_supported_cards_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        val labels = supportedCards.map { snapshot ->
            val applied = if (BankCardRuleRepository.findRule(snapshot) != null) getString(R.string.rule_applied_short) else getString(R.string.rule_default_short)
            "${snapshot.title} · ${snapshot.categoryLabel} · ${snapshot.secondaryLabel} · $applied"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.apply_asset_title, asset.displayName))
            .setItems(labels) { _, which ->
                val snapshot = supportedCards[which]
                BankCardRuleRepository.upsertRule(snapshot, asset)
                refreshAssets()
                (activity as? MainActivity)?.showMessage(getString(R.string.rule_saved_message, snapshot.title))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAssetActions(asset: CardAsset): Boolean {
        val actions = arrayOf(
            getString(R.string.asset_action_edit),
            getString(R.string.asset_action_replace),
            getString(R.string.asset_action_delete),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(asset.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> editAsset(asset)
                    1 -> replaceAsset(asset)
                    2 -> deleteAsset(asset)
                }
            }
            .show()
        return true
    }

    private fun editAsset(asset: CardAsset) {
        val sourceFile = File(asset.absolutePath)
        if (!sourceFile.exists()) {
            (activity as? MainActivity)?.showMessage(getString(R.string.asset_missing_message))
            refreshAssets()
            return
        }
        startCrop(Uri.fromFile(sourceFile), PendingCrop(assetId = asset.id, displayName = asset.displayName))
    }

    private fun replaceAsset(asset: CardAsset) {
        pendingCrop = PendingCrop(assetId = asset.id, displayName = asset.displayName)
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        imagePicker.launch(intent)
    }

    private fun deleteAsset(asset: CardAsset) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.asset_delete_title)
            .setMessage(getString(R.string.asset_delete_message, asset.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.asset_action_delete) { _, _ ->
                CardAssetRepository.deleteAsset(asset.id)
                BankCardRuleRepository.removeRulesForAsset(asset.id)
                refreshAssets()
                (activity as? MainActivity)?.showMessage(getString(R.string.asset_deleted_message, asset.displayName))
            }
            .show()
    }

    private fun startCrop(sourceUri: Uri, request: PendingCrop) {
        val sourceFile = if (sourceUri.scheme == "content") {
            CardImageProcessor.copyUriToTemp(requireContext(), sourceUri)
        } else {
            File(checkNotNull(sourceUri.path))
        }
        val displayName = if (request.displayName == getString(R.string.asset_default_name)) {
            CardAssetRepository.inferDisplayName(sourceFile.name)
        } else {
            request.displayName
        }
        val outputFile = CardAssetRepository.createTempFile("ucrop_")
        pendingCrop = request.copy(displayName = displayName, outputFile = outputFile)
        // Resolve crop UI colors from the theme so the cropper matches dynamic
        // color and the user's light/dark preference.
        val rootView = binding.root
        val toolbarBg = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorSurface)
        val toolbarFg = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorOnSurface)
        val activeAccent = MaterialColors.getColor(rootView, androidx.appcompat.R.attr.colorPrimary)
        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
            setToolbarColor(toolbarBg)
            setToolbarWidgetColor(toolbarFg)
            setActiveControlsWidgetColor(activeAccent)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
        }
        try {
            val cropConfig = CropConfig.load(requireContext())
            val intent = UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(outputFile))
                .withAspectRatio(cropConfig.aspectX.toFloat(), cropConfig.aspectY.toFloat())
                .withMaxResultSize(cropConfig.maxWidth, cropConfig.maxHeight)
                .withOptions(options)
                .getIntent(requireContext())
            val resolved = intent.resolveActivity(requireContext().packageManager)
            if (resolved == null) {
                pendingCrop = null
                (activity as? MainActivity)?.showMessage(getString(R.string.asset_crop_unavailable_message))
                return
            }
            cropLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pendingCrop = null
            (activity as? MainActivity)?.showMessage(getString(R.string.asset_crop_unavailable_message))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class PendingCrop(
        val assetId: String? = null,
        val displayName: String = "",
        val outputFile: File = CardAssetRepository.createTempFile("ucrop_init_"),
    )

    companion object {
        const val TAG = "studio"
    }
}
