package com.luoshui.paycardeditor.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Render spec for one row. The container collects every spec before rendering so first, middle,
 * and last corner roles are assigned unambiguously.
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
 * DSL scope for rows that receive first, middle, and last roles from the container.
 *
 * Row builders synchronously append specs to [pending]. [SegmentedListContainer] reads that list
 * immediately after `content()` returns, then renders all rows in one pass. [pending] is cleared
 * before each container composition.
 *
 * Synchronous registration invariants:
 * - Call only `SegmentedListItem` or `SettingsXxxRow` extensions directly inside
 *   `SegmentedListContainer { ... }`.
 * - Do not defer spec registration to `LaunchedEffect`, `SideEffect`, or child coroutines.
 * - Do not nest another `SegmentedListContainer` inside this scope; use a normal list container
 *   outside this DSL when nesting is needed.
 */
class SegmentedListScope internal constructor() {
    internal val pending: MutableList<SegmentedListItemSpec> = mutableListOf()

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
        pending.add(
            SegmentedListItemSpec(
                title = title,
                summary = summary,
                leading = leading,
                trailing = trailing,
                enabled = enabled,
                onClick = onClick,
                modifier = modifier,
            )
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
    scope.pending.clear()
    scope.content()
    val items = scope.pending.toList()

    val largeCorner = 24.dp
    val containerShape = RoundedCornerShape(largeCorner)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            items.forEachIndexed { index, spec ->
                val isFirst = index == 0
                val isLast = index == items.lastIndex
                val itemShape: Shape = when {
                    isFirst && isLast -> RoundedCornerShape(largeCorner)
                    isFirst -> RoundedCornerShape(
                        topStart = largeCorner,
                        topEnd = largeCorner,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    )
                    isLast -> RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = largeCorner,
                        bottomEnd = largeCorner,
                    )
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    modifier = Modifier
                        .clip(itemShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    RenderSegmentedItem(spec)
                }
                if (!isLast) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
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
