package com.luoshui.paycardeditor.feature.settings

import androidx.annotation.StringRes
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

/** Shared validated editor for paired positive integer settings. */
@Composable
internal fun DualNumberEditorDialog(
    initialFirst: Int,
    initialSecond: Int,
    validRange: IntRange,
    @StringRes titleRes: Int,
    @StringRes firstLabelRes: Int,
    @StringRes secondLabelRes: Int,
    @StringRes errorRes: Int,
    onSave: (first: Int, second: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by remember { mutableStateOf(initialFirst.toString()) }
    var second by remember { mutableStateOf(initialSecond.toString()) }
    val firstInt = first.toIntOrNull()
    val secondInt = second.toIntOrNull()
    val firstValid = firstInt != null && firstInt in validRange
    val secondValid = secondInt != null && secondInt in validRange
    val valid = firstValid && secondValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    NumberField(
                        value = first,
                        onValueChange = { first = it },
                        labelRes = firstLabelRes,
                        isError = !firstValid,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    NumberField(
                        value = second,
                        onValueChange = { second = it },
                        labelRes = secondLabelRes,
                        isError = !secondValid,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(errorRes, validRange.first, validRange.last),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(checkNotNull(firstInt), checkNotNull(secondInt)) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.settings_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        isError = isError,
        singleLine = true,
    )
}
