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
 * Editor dialog for the uCrop aspect ratio.
 *
 * Values must stay within `CropConfig.ASPECT_MIN..CropConfig.ASPECT_MAX`.
 * Input accepts digits only; invalid or empty values disable saving and show an error.
 */
@Composable
fun CropAspectEditorDialog(
    initialX: Int,
    initialY: Int,
    onSave: (x: Int, y: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var x by remember { mutableStateOf(initialX.toString()) }
    var y by remember { mutableStateOf(initialY.toString()) }
    val xInt = x.toIntOrNull()
    val yInt = y.toIntOrNull()
    val xValid = xInt != null && xInt in CropConfig.ASPECT_MIN..CropConfig.ASPECT_MAX
    val yValid = yInt != null && yInt in CropConfig.ASPECT_MIN..CropConfig.ASPECT_MAX
    val valid = xValid && yValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialog_aspect_title)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = x,
                        onValueChange = { x = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.settings_field_aspect_x)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = !xValid,
                        singleLine = true,
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = y,
                        onValueChange = { y = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.settings_field_aspect_y)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = !yValid,
                        singleLine = true,
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_error_aspect,
                            CropConfig.ASPECT_MIN,
                            CropConfig.ASPECT_MAX,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(xInt!!, yInt!!) },
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
