package com.luoshui.paycardeditor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings navigation row. Clicking the row invokes [onClick], and the default trailing content is
 * a right arrow.
 *
 * Must be used inside the [SegmentedListScope] provided by [SegmentedListContainer] or
 * [SettingsSection]. Custom trailing content can show a compact state summary.
 */
@Composable
fun SegmentedListScope.SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        trailing = trailing ?: {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = modifier,
    )
}
