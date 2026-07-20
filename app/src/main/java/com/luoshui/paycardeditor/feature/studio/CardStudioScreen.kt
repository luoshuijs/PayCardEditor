package com.luoshui.paycardeditor.feature.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.data.CardAssetRepository
import com.luoshui.paycardeditor.ui.EmptyErrorEvents
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.ui.components.TonalCard
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Card studio screen.
 *
 * The caller supplies state and events. This screen bridges image picking and
 * UCrop launchers, then reports [CardStudioEvent.CropResult] for persistence.
 * [com.luoshui.paycardeditor.ui.components.TonalCard] wraps asset cells and
 * [coil.compose.AsyncImage] loads local asset files.
 *
 * UCrop flow:
 * 1. The FAB launches the system image picker.
 * 2. The selected image is copied to cache and passed to UCrop.
 * 3. UCrop output is rounded and emitted as [CardStudioEvent.CropResult].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardStudioScreen(
    uiState: CardStudioUiState,
    onEvent: (CardStudioEvent) -> Unit,
    showMessage: (CharSequence) -> Unit,
    modifier: Modifier = Modifier,
    errorEvents: SharedFlow<UiText> = EmptyErrorEvents,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Keep toast collection optional so Compose UI tests can render without a ViewModel.
    LaunchedEffect(errorEvents) {
        errorEvents.collect { uiText ->
            val message = context.getString(uiText.resId, *uiText.args.toTypedArray())
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // pendingCrop links picker, crop, and save callbacks across recompositions.
    var pendingCrop by remember { mutableStateOf<PendingCrop?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val request = pendingCrop
        if (request == null || result.resultCode != Activity.RESULT_OK) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        if (data == null) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        val outputUri = UCrop.getOutput(data)
        // buildUCropIntent assigns outputFile before UCrop launches; fail fast if that contract breaks.
        val outputFile = File(
            outputUri?.path ?: request.outputFile?.absolutePath
                ?: error("PendingCrop.outputFile missing — buildUCropIntent 未填充就触发了 cropLauncher")
        )
        coroutineScope.launch {
            val roundedFile = withContext(Dispatchers.IO) {
                val rounded = CardAssetRepository.createTempFile("rounded_")
                CardImageProcessor.makeRoundedPng(outputFile, rounded)
                rounded
            }
            onEvent(
                CardStudioEvent.CropResult(
                    croppedFile = roundedFile,
                    displayName = request.displayName,
                    existingAssetId = request.assetId,
                )
            )
            pendingCrop = null
            showMessage(
                context.getString(
                    if (request.assetId == null) {
                        R.string.asset_saved_message
                    } else {
                        R.string.asset_updated_message
                    },
                    request.displayName,
                )
            )
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val request = pendingCrop
        if (request == null || result.resultCode != Activity.RESULT_OK) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        val sourceUri = result.data?.data
        if (sourceUri == null) {
            pendingCrop = null
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val intent = withContext(Dispatchers.IO) {
                buildUCropIntent(context, sourceUri, request)
            }
            if (intent == null) {
                pendingCrop = null
                showMessage(context.getString(R.string.asset_crop_unavailable_message))
                return@launch
            }
            // buildUCropIntent returns pending crop data with outputFile and displayName resolved.
            pendingCrop = intent.second
            cropLauncher.launch(intent.first)
        }
    }

    val launchPick: (PendingCrop) -> Unit = { request ->
        pendingCrop = request
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickerLauncher.launch(intent)
    }

    val launchEdit: (CardAsset) -> Unit = { asset ->
        coroutineScope.launch {
            val sourceFile = File(asset.absolutePath)
            if (!sourceFile.exists()) {
                onEvent(CardStudioEvent.Refresh)
                showMessage(context.getString(R.string.asset_missing_message))
                return@launch
            }
            val request = PendingCrop(assetId = asset.id, displayName = asset.displayName)
            val intent = withContext(Dispatchers.IO) {
                buildUCropIntent(context, Uri.fromFile(sourceFile), request)
            }
            if (intent == null) {
                showMessage(context.getString(R.string.asset_crop_unavailable_message))
                return@launch
            }
            pendingCrop = intent.second
            cropLauncher.launch(intent.first)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                launchPick(
                    PendingCrop(
                        assetId = null,
                        displayName = context.getString(R.string.asset_default_name),
                    )
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.studio_add_asset))
            }
        },
    ) { innerPadding ->
        CardStudioContent(
            uiState = uiState,
            innerPadding = innerPadding,
            onEvent = onEvent,
        )
    }

    // ----- Dialog layer -----

    val applySheet = uiState.applySheet
    if (applySheet != null) {
        ApplyAssetDialog(
            sheet = applySheet,
            onPick = { snapshot ->
                onEvent(CardStudioEvent.ApplyAssetToSnapshot(applySheet.asset, snapshot))
                showMessage(context.getString(R.string.rule_saved_message, snapshot.title))
            },
            onDismiss = { onEvent(CardStudioEvent.DismissApplySheet) },
        )
    }

    val actionsSheet = uiState.actionsSheet
    if (actionsSheet != null) {
        AssetActionsDialog(
            asset = actionsSheet.asset,
            onEdit = {
                onEvent(CardStudioEvent.DismissActionsSheet)
                launchEdit(actionsSheet.asset)
            },
            onReplace = {
                onEvent(CardStudioEvent.DismissActionsSheet)
                launchPick(
                    PendingCrop(
                        assetId = actionsSheet.asset.id,
                        displayName = actionsSheet.asset.displayName,
                    )
                )
            },
            onDelete = {
                onEvent(CardStudioEvent.ConfirmDeleteAsset(actionsSheet.asset))
            },
            onDismiss = { onEvent(CardStudioEvent.DismissActionsSheet) },
        )
    }

    val pendingDelete = uiState.pendingDeleteAsset
    if (pendingDelete != null) {
        DeleteAssetConfirmDialog(
            asset = pendingDelete,
            onConfirm = {
                onEvent(CardStudioEvent.RemoveAsset(pendingDelete))
                onEvent(CardStudioEvent.DismissDeleteConfirm)
                showMessage(context.getString(R.string.asset_deleted_message, pendingDelete.displayName))
            },
            onDismiss = { onEvent(CardStudioEvent.DismissDeleteConfirm) },
        )
    }

    // Refresh when first composed because the hook process may update SharedPreferences elsewhere.
    LaunchedEffect(Unit) {
        onEvent(CardStudioEvent.Refresh)
    }
}

@Composable
private fun CardStudioContent(
    uiState: CardStudioUiState,
    innerPadding: PaddingValues,
    onEvent: (CardStudioEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Header summary for asset count and bound rule count.
        TonalCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.studio_section_eyebrow),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.asset_count_format, uiState.assets.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.asset_assignment_hint, uiState.totalAssignments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.assets.isEmpty() && !uiState.isLoading) {
            EmptyAssetsHint()
        } else {
            // Adaptive grid falls back to one column on narrow widths.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(bottom = 96.dp), // Keep cells clear of the FAB.
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.assets, key = { it.id }) { asset ->
                    AssetGridItem(
                        asset = asset,
                        assignmentCount = uiState.assignmentCountByAssetId[asset.id] ?: 0,
                        onClick = { onEvent(CardStudioEvent.RequestApplyAsset(asset)) },
                        onLongClick = { onEvent(CardStudioEvent.RequestAssetActions(asset)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAssetsHint() {
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.studio_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.studio_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Single asset grid cell.
 *
 * Long-click handling is attached to the inner Box because [TonalCard] does not
 * expose an onLongClick parameter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetGridItem(
    asset: CardAsset,
    assignmentCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    TonalCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Column(Modifier.padding(12.dp)) {
                // File models use Coil's built-in FileFetcher and avoid FileProvider setup.
                AsyncImage(
                    model = File(asset.absolutePath),
                    contentDescription = asset.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = asset.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.asset_item_subtitle, assignmentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Dialog for applying one asset to a card.
 *
 * [ApplyCandidate.alreadyCustomized] is precomputed by the ViewModel off the UI
 * thread, so dialog rendering only reads memory.
 */
@Composable
private fun ApplyAssetDialog(
    sheet: ApplyAssetSheet,
    onPick: (com.luoshui.paycardeditor.model.CardSnapshot) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.apply_asset_title, sheet.asset.displayName))
        },
        text = {
            Column {
                sheet.candidates.forEach { candidate ->
                    val snapshot = candidate.snapshot
                    val hasAppliedRule = candidate.alreadyCustomized
                    TextButton(
                        onClick = { onPick(snapshot) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = snapshot.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${snapshot.categoryLabel} · ${snapshot.secondaryLabel} · " +
                                    if (hasAppliedRule) {
                                        stringResource(R.string.rule_applied_short)
                                    } else {
                                        stringResource(R.string.rule_default_short)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AssetActionsDialog(
    asset: CardAsset,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(asset.displayName) },
        text = {
            Column {
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asset_action_edit))
                }
                TextButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asset_action_replace))
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.asset_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteAssetConfirmDialog(
    asset: CardAsset,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.asset_delete_title)) },
        text = {
            Text(stringResource(R.string.asset_delete_message, asset.displayName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.asset_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

// ----- UCrop intent helper -----

/**
 * Pending crop state shared across image-picker and crop launchers.
 *
 * [outputFile] is null before the picker returns because the destination file
 * cannot be known until [buildUCropIntent] sees the source image. The crop
 * callback asserts non-null output after that helper has assigned it.
 */
private data class PendingCrop(
    val assetId: String? = null,
    val displayName: String = "",
    val outputFile: File? = null,
)

/**
 * Builds the UCrop intent and the updated pending crop state.
 *
 * Returns null when no UCrop activity can handle the intent. The returned
 * [PendingCrop] has an assigned [PendingCrop.outputFile] and a display name
 * inferred from the source file when the caller did not provide one.
 */
private fun buildUCropIntent(
    context: android.content.Context,
    sourceUri: Uri,
    request: PendingCrop,
): Pair<Intent, PendingCrop>? {
    val sourceFile = if (sourceUri.scheme == "content") {
        CardImageProcessor.copyUriToTemp(context, sourceUri)
    } else {
        File(checkNotNull(sourceUri.path))
    }
    val defaultName = context.getString(R.string.asset_default_name)
    val displayName = if (request.displayName == defaultName) {
        CardAssetRepository.inferDisplayName(sourceFile.name)
    } else {
        request.displayName
    }
    val outputFile = CardAssetRepository.createTempFile("ucrop_")
    val updated = request.copy(displayName = displayName, outputFile = outputFile)

    val options = UCrop.Options().apply {
        setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
        setHideBottomControls(false)
        setFreeStyleCropEnabled(false)
        setShowCropGrid(true)
        setShowCropFrame(true)
    }
    val cropConfig = CropConfig.load(context)
    val intent = UCrop.of(Uri.fromFile(sourceFile), Uri.fromFile(outputFile))
        .withAspectRatio(cropConfig.aspectX.toFloat(), cropConfig.aspectY.toFloat())
        .withMaxResultSize(cropConfig.maxWidth, cropConfig.maxHeight)
        .withOptions(options)
        .getIntent(context)
    if (intent.resolveActivity(context.packageManager) == null) {
        return null
    }
    return intent to updated
}
