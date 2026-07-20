package com.luoshui.paycardeditor.ui.components

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings radio row. Clicking the row or trailing [RadioButton] invokes [onSelect] with [value].
 *
 * Must be used inside the [SegmentedListScope] provided by [SegmentedListContainer] or
 * [SettingsSection]. The caller owns `selected` state and compares it with [value].
 */
@Composable
fun <T> SegmentedListScope.SettingsRadioRow(
    title: String,
    value: T,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { if (enabled) onSelect(value) },
        trailing = {
            RadioButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}
