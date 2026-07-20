package com.luoshui.paycardeditor.feature.preview

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.ui.EmptyErrorEvents
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.ui.components.StatusTag
import com.luoshui.paycardeditor.ui.components.StatusTagTone
import com.luoshui.paycardeditor.ui.components.TonalCard
import com.luoshui.paycardeditor.ui.components.WarningCard
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Main card preview screen.
 *
 * State and events are supplied by the caller. Shared atoms provide card,
 * warning, and status rendering. [coil.compose.AsyncImage] handles remote and
 * local image sources, with placeholder and error painters to avoid blank image
 * cells while loading fails or settles.
 */
@Composable
internal fun CardPreviewScreen(
    uiState: CardPreviewUiState,
    onEvent: (CardPreviewEvent) -> Unit,
    modifier: Modifier = Modifier,
    errorEvents: SharedFlow<UiText> = EmptyErrorEvents,
    effects: SharedFlow<CardPreviewEffect> = EmptyCardPreviewEffects,
) {
    val context = LocalContext.current
    // Keep toast collection optional so Compose UI tests can render without a ViewModel.
    LaunchedEffect(errorEvents) {
        errorEvents.collect { uiText ->
            val message = context.getString(uiText.resId, *uiText.args.toTypedArray())
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    // Success feedback uses its own flow so restore failures emit only the error toast.
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is CardPreviewEffect.ShowMessage -> {
                    val message = context.getString(
                        effect.message.resId,
                        *effect.message.args.toTypedArray(),
                    )
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (uiState.warning.isNotBlank()) {
            WarningCard(
                title = stringResource(R.string.preview_section_eyebrow),
                body = uiState.warning,
                icon = Icons.Filled.WarningAmber,
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = stringResource(R.string.preview_count_format, uiState.items.size),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        if (uiState.items.isEmpty() && !uiState.isLoading) {
            EmptyPreviewState(modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.items, key = { it.snapshot.key }) { item ->
                    CardPreviewListItem(
                        item = item,
                        onClick = { onEvent(CardPreviewEvent.RequestActions(item.snapshot)) },
                    )
                }
            }
        }
    }

    uiState.pendingAction?.let { action ->
        CardPreviewActionsDialog(
            action = action,
            onDismiss = { onEvent(CardPreviewEvent.DismissActions) },
            onConfirmRestore = { onEvent(CardPreviewEvent.ConfirmRestore(action.snapshot)) },
        )
    }
}

@Composable
private fun EmptyPreviewState(modifier: Modifier = Modifier) {
    TonalCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.preview_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.preview_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun CardPreviewListItem(
    item: CardPreviewItem,
    onClick: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The model can be remote, local, or null; fallback painters keep the row stable.
            AsyncImage(
                model = item.displayImageUrl,
                contentDescription = item.snapshot.title,
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                error = painterResource(android.R.drawable.ic_menu_gallery),
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.snapshot.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.preview_item_subtitle,
                        item.snapshot.categoryLabel,
                        item.snapshot.secondaryLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                StatusTag(
                    text = stringResource(
                        if (item.appliedLabel) R.string.rule_applied_short
                        else R.string.rule_default_short,
                    ),
                    tone = if (item.appliedLabel) StatusTagTone.Success else StatusTagTone.Neutral,
                )
            }
        }
    }
}

@Composable
private fun CardPreviewActionsDialog(
    action: CardPreviewAction,
    onDismiss: () -> Unit,
    onConfirmRestore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_dialog_title)) },
        text = {
            // Render detail rows as themed text rather than a Spannable message.
            Column {
                Text(
                    text = action.snapshot.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.preview_detail_type,
                        action.snapshot.categoryLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.preview_detail_identifier,
                        action.snapshot.secondaryLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.preview_detail_status,
                        stringResource(
                            if (action.canRestore) R.string.rule_applied_short
                            else R.string.rule_default_short,
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            // Only cards with a custom rule expose the restore action.
            if (action.canRestore) {
                TextButton(onClick = onConfirmRestore) {
                    Text(stringResource(R.string.preview_restore_action))
                }
            } else {
                // AlertDialog requires a confirm slot; an empty Box renders no pixels.
                Box {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Empty effect flow used as the default so Compose UI tests can render without a ViewModel.
 */
private val EmptyCardPreviewEffects: SharedFlow<CardPreviewEffect> =
    MutableSharedFlow<CardPreviewEffect>().asSharedFlow()
