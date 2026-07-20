package com.luoshui.paycardeditor.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Single card atom using Material3 tonal styling.
 *
 * Screens use this atom instead of importing Material3 card primitives directly. The default
 * container color is `surfaceContainerLow` and no stroke is applied.
 *
 * Nullable [shape] and [containerColor] distinguish "use theme default" from explicit overrides
 * such as transparent colors or rectangular shapes.
 */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val finalShape = shape ?: MaterialTheme.shapes.large
    val finalColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = finalShape,
            colors = CardDefaults.cardColors(containerColor = finalColor),
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = finalShape,
            colors = CardDefaults.cardColors(containerColor = finalColor),
            content = content,
        )
    }
}
