package com.luoshui.paycardeditor.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.feature.studio.CropConfig

/**
 * Editor dialog for the maximum uCrop output size.
 *
 * Values must stay within `CropConfig.SIZE_MIN..CropConfig.SIZE_MAX`.
 */
@Composable
fun CropSizeEditorDialog(
    initialWidth: Int,
    initialHeight: Int,
    onSave: (w: Int, h: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var w by remember { mutableStateOf(initialWidth.toString()) }
    var h by remember { mutableStateOf(initialHeight.toString()) }
    val wInt = w.toIntOrNull()
    val hInt = h.toIntOrNull()
    val wValid = wInt != null && wInt in CropConfig.SIZE_MIN..CropConfig.SIZE_MAX
    val hValid = hInt != null && hInt in CropConfig.SIZE_MIN..CropConfig.SIZE_MAX
    val valid = wValid && hValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialog_size_title)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = w,
                        onValueChange = { w = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.settings_field_max_width)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = !wValid,
                        singleLine = true,
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = h,
                        onValueChange = { h = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.settings_field_max_height)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = !hValid,
                        singleLine = true,
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_error_size,
                            CropConfig.SIZE_MIN,
                            CropConfig.SIZE_MAX,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(wInt!!, hInt!!) },
                enabled = valid,
            ) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}
