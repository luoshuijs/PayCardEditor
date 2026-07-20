package com.luoshui.paycardeditor.feature.settings

import androidx.compose.runtime.Composable
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.feature.studio.CropConfig

/** Editor dialog for the maximum uCrop output size. */
@Composable
fun CropSizeEditorDialog(
    initialWidth: Int,
    initialHeight: Int,
    onSave: (w: Int, h: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DualNumberEditorDialog(
        initialFirst = initialWidth,
        initialSecond = initialHeight,
        validRange = CropConfig.SIZE_MIN..CropConfig.SIZE_MAX,
        titleRes = R.string.settings_dialog_size_title,
        firstLabelRes = R.string.settings_field_max_width,
        secondLabelRes = R.string.settings_field_max_height,
        errorRes = R.string.settings_error_size,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}
