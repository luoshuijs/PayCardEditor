package com.luoshui.paycardeditor.ui.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings switch row. Clicking the row or trailing [Switch] toggles the value.
 *
 * Must be used inside the [SegmentedListScope] provided by [SegmentedListContainer] or
 * [SettingsSection].
 */
@Composable
fun SegmentedListScope.SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}
