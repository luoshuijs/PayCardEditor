package com.luoshui.paycardeditor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Settings dropdown row. Clicking the row opens the menu, and the summary defaults to the
 * selected option label.
 *
 * Must be used inside the [SegmentedListScope] provided by [SegmentedListContainer] or
 * [SettingsSection].
 */
@Composable
fun <T> SegmentedListScope.SettingsDropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    SegmentedListItem(
        title = title,
        summary = summary ?: optionLabel(selected),
        enabled = enabled,
        onClick = { if (enabled) expanded = true },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(opt)) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        },
                    )
                }
            }
        },
        modifier = modifier,
    )
}
