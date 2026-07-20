package com.luoshui.paycardeditor.feature.settings

import androidx.compose.runtime.Composable
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.feature.studio.CropConfig

/** Editor dialog for the uCrop aspect ratio. */
@Composable
fun CropAspectEditorDialog(
    initialX: Int,
    initialY: Int,
    onSave: (x: Int, y: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DualNumberEditorDialog(
        initialFirst = initialX,
        initialSecond = initialY,
        validRange = CropConfig.ASPECT_MIN..CropConfig.ASPECT_MAX,
        titleRes = R.string.settings_dialog_aspect_title,
        firstLabelRes = R.string.settings_field_aspect_x,
        secondLabelRes = R.string.settings_field_aspect_y,
        errorRes = R.string.settings_error_aspect,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}
