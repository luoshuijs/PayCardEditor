package com.luoshui.paycardeditor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

/**
 * Render spec for one row.
 */
internal data class SegmentedListItemSpec(
    val title: String,
    val summary: String?,
    val leading: (@Composable () -> Unit)?,
    val trailing: (@Composable () -> Unit)?,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val modifier: Modifier,
)

/**
 * DSL scope for rows rendered by [SegmentedListContainer].
 */
class SegmentedListScope internal constructor() {
    @Composable
    fun SegmentedListItem(
        title: String,
        modifier: Modifier = Modifier,
        summary: String? = null,
        leading: @Composable (() -> Unit)? = null,
        trailing: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        RenderSegmentedItem(
            SegmentedListItemSpec(
                title = title,
                summary = summary,
                leading = leading,
                trailing = trailing,
                enabled = enabled,
                onClick = onClick,
                modifier = modifier,
            ),
        )
    }
}

/**
 * Segmented list group with rounded outer corners and square middle rows.
 *
 * Rows share a `surfaceContainerLow` background and 1dp dividers between adjacent items.
 *
 * Usage:
 * ```
 * SegmentedListContainer {
 *     SegmentedListItem(title = "...")
 *     SegmentedListItem(title = "...", onClick = { ... })
 * }
 * ```
 */
@Composable
fun SegmentedListContainer(
    modifier: Modifier = Modifier,
    content: @Composable SegmentedListScope.() -> Unit,
) {
    val scope = remember { SegmentedListScope() }
    val largeCorner = 24.dp
    val containerShape = RoundedCornerShape(largeCorner)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = containerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val itemConstraints = constraints.copy(minHeight = 0)
            val itemPlaceables = subcompose(SegmentedListSlot.Items) {
                scope.content()
            }.map { it.measure(itemConstraints) }
            val width = (itemPlaceables.maxOfOrNull { it.width } ?: constraints.minWidth)
                .coerceIn(constraints.minWidth, constraints.maxWidth)
            val dividerPlaceables = subcompose(SegmentedListSlot.Dividers) {
                repeat((itemPlaceables.size - 1).coerceAtLeast(0)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = dividerColor,
                    )
                }
            }.map {
                it.measure(
                    constraints.copy(
                        minWidth = width,
                        maxWidth = width,
                        minHeight = 0,
                    ),
                )
            }
            val contentHeight = itemPlaceables.sumOf { it.height } +
                dividerPlaceables.sumOf { it.height }

            val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                itemPlaceables.forEachIndexed { index, item ->
                    item.placeRelative(0, y)
                    y += item.height
                    dividerPlaceables.getOrNull(index)?.let { divider ->
                        divider.placeRelative(0, y)
                        y += divider.height
                    }
                }
            }
        }
    }
}

private enum class SegmentedListSlot {
    Items,
    Dividers,
}

@Composable
private fun RenderSegmentedItem(spec: SegmentedListItemSpec) {
    val clickable = spec.onClick != null && spec.enabled
    val rowModifier = spec.modifier
        .fillMaxWidth()
        .let { m ->
            if (clickable) m.clickable(onClick = spec.onClick!!) else m
        }
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.leading != null) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                spec.leading.invoke()
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (spec.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (spec.summary != null) {
                Text(
                    text = spec.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (spec.enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
        if (spec.trailing != null) {
            Spacer(Modifier.width(12.dp))
            spec.trailing.invoke()
        }
    }
}
