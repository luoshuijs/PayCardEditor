package com.luoshui.paycardeditor.feature.troubleshoot

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshotFormatter
import com.luoshui.paycardeditor.model.TroubleshootState
import com.luoshui.paycardeditor.ui.EmptyErrorEvents
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.ui.components.TonalCard
import com.luoshui.paycardeditor.ui.components.UiErrorEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Troubleshooting screen Compose entry.
 *
 * Two [TonalCard] sections render debug information and the hook method list.
 * [buildHookMethodsAnnotated] converts the plain text hook protocol into a
 * colored [AnnotatedString]. Clipboard and Toast side effects are driven by
 * [effects] so the ViewModel remains JVM-testable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TroubleshootScreen(
    uiState: TroubleshootUiState,
    effects: Flow<TroubleshootEffect>,
    onEvent: (TroubleshootEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    errorEvents: SharedFlow<UiText> = EmptyErrorEvents,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is TroubleshootEffect.CopyToClipboard -> {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText(effect.label, effect.text)),
                    )
                    Toast.makeText(context, R.string.troubleshoot_copy_done, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    UiErrorEffect(errorEvents)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.troubleshoot_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.troubleshoot_back_content_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // LazyColumn keeps the layout ready for additional diagnostic sections.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = TroubleshootSection.entries) { section ->
                TroubleshootSectionCard(
                    section = section,
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/** Single data source for the diagnostic section rows. */
private enum class TroubleshootSection { Debug, Hooks }

@Composable
private fun TroubleshootSectionCard(
    section: TroubleshootSection,
    uiState: TroubleshootUiState,
    onEvent: (TroubleshootEvent) -> Unit,
) {
    val state = uiState.troubleshootState
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Section title uses the eyebrow-style label treatment.
            Text(
                text = when (section) {
                    TroubleshootSection.Debug -> stringResource(R.string.troubleshoot_debug_title)
                    TroubleshootSection.Hooks -> stringResource(R.string.troubleshoot_hook_list_title)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (state == null) {
                // Show a placeholder until the first refresh completes.
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.troubleshoot_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            when (section) {
                TroubleshootSection.Debug -> TroubleshootDebugBody(state = state, onEvent = onEvent)
                TroubleshootSection.Hooks -> TroubleshootHooksBody(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun TroubleshootDebugBody(
    state: TroubleshootState,
    onEvent: (TroubleshootEvent) -> Unit,
) {
    // Keep updated-at metadata close to the section title, with debug text below it.
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.troubleshoot_updated_at, CardSnapshotFormatter.formatTimestamp(state.updatedAt)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = state.debugInfo,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onEvent(TroubleshootEvent.CopyDebug) }) {
        Text(stringResource(R.string.troubleshoot_copy_action))
    }
}

@Composable
private fun TroubleshootHooksBody(
    state: TroubleshootState,
    onEvent: (TroubleshootEvent) -> Unit,
) {
    val resolvedColor = MaterialTheme.colorScheme.primary
    val missingColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hooksAnnotated = buildHookMethodsAnnotated(
        text = state.hookMethods,
        resolvedColor = resolvedColor,
        missingColor = missingColor,
        secondaryColor = secondaryColor,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = hooksAnnotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onEvent(TroubleshootEvent.CopyHooks) }) {
        Text(stringResource(R.string.troubleshoot_copy_action))
    }
}

/**
 * Renders the plain text hook method protocol as a colored [AnnotatedString].
 *
 * Protocol:
 * - Blocks are separated by `"\n\n"`.
 * - Each block has at least one line.
 * - A block whose trimmed last line is `"= (void*)0"` is treated as missing.
 * - The first and last lines use the status color, and the second line uses [secondaryColor].
 *
 * Content is preserved exactly; display text and Clipboard text stay identical.
 * Colors are parameters so tests can verify [SpanStyle] assignment without reading theme state.
 */
internal fun buildHookMethodsAnnotated(
    text: String,
    resolvedColor: Color,
    missingColor: Color,
    secondaryColor: Color,
): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        val blocks = text.split("\n\n")
        blocks.forEachIndexed { blockIndex, block ->
            if (blockIndex > 0) append("\n\n")
            val lines = block.split("\n")
            val isMissing = lines.lastOrNull()?.trim() == "= (void*)0"
            val statusColor = if (isMissing) missingColor else resolvedColor
            lines.forEachIndexed { lineIndex, line ->
                val color = when {
                    lineIndex == 0 -> statusColor
                    lineIndex == 1 && lines.size >= 2 -> secondaryColor
                    lineIndex == lines.lastIndex && lines.size >= 2 -> statusColor
                    else -> Color.Unspecified
                }
                if (color == Color.Unspecified) {
                    append(line)
                } else {
                    withStyle(SpanStyle(color = color)) { append(line) }
                }
                if (lineIndex < lines.lastIndex) append("\n")
            }
        }
    }
}

/**
 * Empty effect source used by screen tests that render without a ViewModel.
 */
@Suppress("unused")
internal val EmptyTroubleshootEffects: Flow<TroubleshootEffect> =
    MutableSharedFlow<TroubleshootEffect>().asSharedFlow()
