package com.luoshui.paycardeditor.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.databinding.DialogCropAspectBinding
import com.luoshui.paycardeditor.databinding.DialogCropSizeBinding
import com.luoshui.paycardeditor.databinding.FragmentSettingsBinding
import com.luoshui.paycardeditor.feature.studio.CropConfig

/**
 * Settings list page.
 *
 * Two read-only rows summarise the current UCrop sizing parameters. Tapping a
 * row opens a Material dialog containing the editable inputs for that
 * parameter group. Persisting is gated by the dialog's "保存" button — the
 * list itself never holds dirty state.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshRows()
        binding.rowCropAspect.setOnClickListener { showAspectDialog() }
        binding.rowCropSize.setOnClickListener { showSizeDialog() }
    }

    override fun onResume() {
        super.onResume()
        // Cheap safety net: if anything outside the dialog mutated prefs while
        // we were off-screen, the rows still reflect truth on return.
        refreshRows()
    }

    private fun refreshRows() {
        val values = CropConfig.load(requireContext())
        binding.textCropAspectValue.text = getString(
            R.string.settings_value_aspect_format,
            values.aspectX,
            values.aspectY,
        )
        binding.textCropSizeValue.text = getString(
            R.string.settings_value_size_format,
            values.maxWidth,
            values.maxHeight,
        )
    }

    // region Aspect dialog

    private fun showAspectDialog() {
        val current = CropConfig.load(requireContext())
        val dialogBinding = DialogCropAspectBinding.inflate(layoutInflater)
        dialogBinding.inputAspectX.setText(current.aspectX.toString())
        dialogBinding.inputAspectY.setText(current.aspectY.toString())
        dialogBinding.inputAspectX.doAfterTextChanged { dialogBinding.inputLayoutAspectX.error = null }
        dialogBinding.inputAspectY.doAfterTextChanged { dialogBinding.inputLayoutAspectY.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_dialog_aspect_title)
            .setView(dialogBinding.root)
            // Positive listener wired below so we can suppress dismissal on
            // validation failure — `setPositiveButton(..., listener)` would
            // always dismiss before we could show inline errors.
            .setPositiveButton(R.string.settings_action_save, null)
            .setNeutralButton(R.string.settings_action_reset, null)
            .setNegativeButton(R.string.settings_action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveAspect(dialogBinding)) {
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialogBinding.inputAspectX.setText(CropConfig.DEFAULT_ASPECT_X.toString())
                dialogBinding.inputAspectY.setText(CropConfig.DEFAULT_ASPECT_Y.toString())
                dialogBinding.inputLayoutAspectX.error = null
                dialogBinding.inputLayoutAspectY.error = null
            }
        }
        dialog.show()
    }

    private fun saveAspect(dialogBinding: DialogCropAspectBinding): Boolean {
        val errMsg = getString(
            R.string.settings_error_aspect,
            CropConfig.ASPECT_MIN,
            CropConfig.ASPECT_MAX,
        )
        val x = parseInRange(
            dialogBinding.inputAspectX,
            dialogBinding.inputLayoutAspectX,
            CropConfig.ASPECT_MIN,
            CropConfig.ASPECT_MAX,
            errMsg,
        ) ?: return false
        val y = parseInRange(
            dialogBinding.inputAspectY,
            dialogBinding.inputLayoutAspectY,
            CropConfig.ASPECT_MIN,
            CropConfig.ASPECT_MAX,
            errMsg,
        ) ?: return false

        // Merge into existing values so the size group is preserved.
        val current = CropConfig.load(requireContext())
        return persist(current.copy(aspectX = x, aspectY = y))
    }

    // endregion
    // region Size dialog

    private fun showSizeDialog() {
        val current = CropConfig.load(requireContext())
        val dialogBinding = DialogCropSizeBinding.inflate(layoutInflater)
        dialogBinding.inputMaxWidth.setText(current.maxWidth.toString())
        dialogBinding.inputMaxHeight.setText(current.maxHeight.toString())
        dialogBinding.inputMaxWidth.doAfterTextChanged { dialogBinding.inputLayoutMaxWidth.error = null }
        dialogBinding.inputMaxHeight.doAfterTextChanged { dialogBinding.inputLayoutMaxHeight.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_dialog_size_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.settings_action_save, null)
            .setNeutralButton(R.string.settings_action_reset, null)
            .setNegativeButton(R.string.settings_action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveSize(dialogBinding)) {
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialogBinding.inputMaxWidth.setText(CropConfig.DEFAULT_MAX_WIDTH.toString())
                dialogBinding.inputMaxHeight.setText(CropConfig.DEFAULT_MAX_HEIGHT.toString())
                dialogBinding.inputLayoutMaxWidth.error = null
                dialogBinding.inputLayoutMaxHeight.error = null
            }
        }
        dialog.show()
    }

    private fun saveSize(dialogBinding: DialogCropSizeBinding): Boolean {
        val errMsg = getString(
            R.string.settings_error_size,
            CropConfig.SIZE_MIN,
            CropConfig.SIZE_MAX,
        )
        val w = parseInRange(
            dialogBinding.inputMaxWidth,
            dialogBinding.inputLayoutMaxWidth,
            CropConfig.SIZE_MIN,
            CropConfig.SIZE_MAX,
            errMsg,
        ) ?: return false
        val h = parseInRange(
            dialogBinding.inputMaxHeight,
            dialogBinding.inputLayoutMaxHeight,
            CropConfig.SIZE_MIN,
            CropConfig.SIZE_MAX,
            errMsg,
        ) ?: return false

        val current = CropConfig.load(requireContext())
        return persist(current.copy(maxWidth = w, maxHeight = h))
    }

    // endregion

    private fun persist(values: CropConfig.Values): Boolean {
        val ok = CropConfig.save(requireContext(), values)
        if (ok) {
            refreshRows()
            Snackbar.make(binding.root, R.string.settings_saved_message, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(
                binding.root,
                R.string.settings_save_failed_message,
                Snackbar.LENGTH_LONG,
            ).show()
        }
        return ok
    }

    /**
     * Parse [edit]'s text as an int in [min..max]. On failure attach
     * [errorMessage] to [layout], move focus there, and return null.
     */
    private fun parseInRange(
        edit: TextInputEditText,
        layout: TextInputLayout,
        min: Int,
        max: Int,
        errorMessage: String,
    ): Int? {
        val raw = edit.text?.toString()?.trim().orEmpty()
        val value = raw.toIntOrNull()
        if (value == null || value !in min..max) {
            layout.error = errorMessage
            edit.requestFocus()
            return null
        }
        return value
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
