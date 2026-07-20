package com.luoshui.paycardeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Status tones mapped to Material3 container/on-container color pairs. */
enum class StatusTagTone { Neutral, Success, Warning, Error, Info }

/**
 * Status pill with a rounded container and label text.
 *
 * Colors are derived from `MaterialTheme.colorScheme.*`, so the component follows light, dark,
 * AMOLED, and Monet theme modes without hardcoded values.
 */
@Composable
fun StatusTag(
    text: String,
    tone: StatusTagTone,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bg: Color
    val fg: Color
    when (tone) {
        StatusTagTone.Neutral -> {
            bg = scheme.surfaceContainerHighest
            fg = scheme.onSurfaceVariant
        }
        StatusTagTone.Success -> {
            bg = scheme.tertiaryContainer
            fg = scheme.onTertiaryContainer
        }
        StatusTagTone.Warning -> {
            bg = scheme.secondaryContainer
            fg = scheme.onSecondaryContainer
        }
        StatusTagTone.Error -> {
            bg = scheme.errorContainer
            fg = scheme.onErrorContainer
        }
        StatusTagTone.Info -> {
            bg = scheme.primaryContainer
            fg = scheme.onPrimaryContainer
        }
    }
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg,
        style = MaterialTheme.typography.labelSmall,
    )
}
