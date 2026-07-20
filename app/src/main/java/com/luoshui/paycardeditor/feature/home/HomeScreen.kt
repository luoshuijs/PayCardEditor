package com.luoshui.paycardeditor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshotFormatter
import com.luoshui.paycardeditor.model.HomeState
import com.luoshui.paycardeditor.model.ModuleStatusLevel
import com.luoshui.paycardeditor.ui.EmptyErrorEvents
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.ui.components.StatusTag
import com.luoshui.paycardeditor.ui.components.StatusTagTone
import com.luoshui.paycardeditor.ui.components.TonalCard
import com.luoshui.paycardeditor.ui.components.UiErrorEffect
import com.luoshui.paycardeditor.ui.components.WarningCard
import kotlinx.coroutines.flow.SharedFlow

/**
 * Home tab screen.
 *
 * The screen is intentionally stateless: service status, snapshot summary,
 * warnings, and navigation actions all flow through [HomeUiState] and
 * [HomeEvent]. `Column + verticalScroll` is used because the item count is
 * fixed and small; eager composition also keeps Robolectric Compose tests able
 * to query every card in zero-size hosts.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    errorEvents: SharedFlow<UiText> = EmptyErrorEvents,
) {
    UiErrorEffect(errorEvents)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ServiceStatusCard(uiState)
        SnapshotSummaryCard(uiState, onEvent)
        val warning = uiState.homeState?.cardState?.warning.orEmpty()
        if (warning.isNotBlank()) {
            WarningCard(
                title = stringResource(R.string.home_snapshot_label),
                body = warning,
            )
        }
        ActionsCard(onEvent)
        Spacer(Modifier.height(8.dp))
    }

    uiState.snapshotDetails?.let { state ->
        HomeSnapshotDetailsDialog(
            state = state,
            onDismiss = { onEvent(HomeEvent.DismissSnapshotDetails) },
        )
    }
}

@Composable
private fun ServiceStatusCard(uiState: HomeUiState) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.home_service_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            val moduleStatus = uiState.homeState?.moduleStatus
            if (moduleStatus != null) {
                StatusTag(
                    text = when (moduleStatus.level) {
                        ModuleStatusLevel.ACTIVE -> stringResource(R.string.home_module_status_active)
                        ModuleStatusLevel.WAITING -> stringResource(R.string.home_module_status_waiting)
                        ModuleStatusLevel.INACTIVE -> stringResource(R.string.home_module_status_inactive)
                    },
                    tone = when (moduleStatus.level) {
                        ModuleStatusLevel.ACTIVE -> StatusTagTone.Success
                        ModuleStatusLevel.WAITING -> StatusTagTone.Info
                        ModuleStatusLevel.INACTIVE -> StatusTagTone.Error
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = moduleStatus.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.home_module_status_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SnapshotSummaryCard(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.home_snapshot_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            val cardState = uiState.homeState?.cardState
            val count = cardState?.cards?.size ?: 0
            Text(
                text = stringResource(R.string.snapshot_count_format, count),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.last_updated_format,
                    CardSnapshotFormatter.formatTimestamp(cardState?.lastUpdated ?: 0L),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.last_source_format,
                    cardState?.lastSource?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.none_label),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { onEvent(HomeEvent.SyncSnapshots) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_action_sync))
            }
        }
    }
}

@Composable
private fun ActionsCard(onEvent: (HomeEvent) -> Unit) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Button(
                onClick = { onEvent(HomeEvent.OpenMiPay) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Wallet, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_action_open_mipay))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onEvent(HomeEvent.OpenTroubleshoot) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_action_troubleshoot))
            }
        }
    }
}
