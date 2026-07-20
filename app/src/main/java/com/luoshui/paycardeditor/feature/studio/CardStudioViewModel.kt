package com.luoshui.paycardeditor.feature.studio

import androidx.lifecycle.viewModelScope
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.ui.BaseViewModel
import com.luoshui.paycardeditor.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for [CardStudioScreen].
 *
 * [BaseViewModel] supplies [viewModelScope] and error events. All data access
 * is injected through reader and writer lambdas, keeping Android Context out of
 * the ViewModel and making unit tests straightforward. [assignmentCounts]
 * batches assignment lookups so refresh performs one rule read and then memory
 * lookups per asset.
 *
 * Operations that touch storage catch failures, emit error events, and leave
 * enough existing state in place for the screen to recover.
 *
 * @param assetReader reads the current asset list.
 * @param assignmentCounts batch query for each asset id to assignment count.
 * @param snapshotsReader returns cards that can accept custom art.
 * @param assetSaver persists a cropped asset and returns the saved asset.
 * @param assetDeleter removes one asset and returns the removed asset when present.
 * @param rulesForAssetRemover removes rules that point at an asset id.
 * @param ruleWriter writes a rule that applies an asset to a card snapshot.
 * @param customizedReader checks whether a candidate snapshot already has custom art.
 */
class CardStudioViewModel(
    private val assetReader: suspend () -> List<CardAsset>,
    private val assignmentCounts: suspend (List<CardAsset>) -> Map<String, Int>,
    private val snapshotsReader: suspend () -> List<CardSnapshot>,
    private val assetSaver: suspend (sourceFile: java.io.File, displayName: String, existingAssetId: String?) -> CardAsset,
    private val assetDeleter: suspend (assetId: String) -> CardAsset?,
    private val rulesForAssetRemover: suspend (assetId: String) -> Unit,
    private val ruleWriter: suspend (snapshot: CardSnapshot, asset: CardAsset) -> Unit,
    private val customizedReader: suspend (CardSnapshot) -> Boolean = { false },
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(CardStudioUiState())
    val uiState: StateFlow<CardStudioUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CardStudioEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<CardStudioEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun handleEvent(event: CardStudioEvent) {
        when (event) {
            CardStudioEvent.Refresh -> viewModelScope.launch { refresh() }

            // The screen launches the picker; the event keeps one screen event API.
            CardStudioEvent.PickImage -> Unit

            is CardStudioEvent.CropResult -> viewModelScope.launch {
                val savedAsset = try {
                    // Reload after writes so in-memory state matches repository storage.
                    assetSaver(event.croppedFile, event.displayName, event.existingAssetId)
                } catch (e: Exception) {
                    emitError(UiText(R.string.error_save_asset_failed))
                    return@launch
                }
                _effects.emit(
                    CardStudioEffect.ShowMessage(
                        UiText(
                            if (event.existingAssetId == null) {
                                R.string.asset_saved_message
                            } else {
                                R.string.asset_updated_message
                            },
                            listOf(savedAsset.displayName),
                        ),
                    ),
                )
                refresh()
            }

            is CardStudioEvent.RemoveAsset -> viewModelScope.launch {
                try {
                    // Asset deletion and rules that point at the asset are separate stores.
                    assetDeleter(event.asset.id)
                    rulesForAssetRemover(event.asset.id)
                } catch (e: Exception) {
                    emitError(UiText(R.string.error_remove_asset_failed))
                    return@launch
                }
                _uiState.value = _uiState.value.copy(pendingDeleteAsset = null)
                _effects.emit(
                    CardStudioEffect.ShowMessage(
                        UiText(R.string.asset_deleted_message, listOf(event.asset.displayName)),
                    ),
                )
                refresh()
            }

            is CardStudioEvent.RequestApplyAsset -> viewModelScope.launch {
                val mapped = try {
                    val candidates = snapshotsReader()
                    if (candidates.isEmpty()) {
                        _effects.emit(
                            CardStudioEffect.ShowMessage(UiText(R.string.no_supported_cards_available)),
                        )
                        return@launch
                    }
                    // Query candidate customization before rendering the dialog.
                    candidates.map { snap ->
                        ApplyCandidate(
                            snapshot = snap,
                            alreadyCustomized = customizedReader(snap),
                        )
                    }
                } catch (e: Exception) {
                    emitError(UiText(R.string.error_load_studio_failed))
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    applySheet = ApplyAssetSheet(
                        asset = event.asset,
                        candidates = mapped,
                    ),
                )
            }

            CardStudioEvent.DismissApplySheet -> {
                _uiState.value = _uiState.value.copy(applySheet = null)
            }

            is CardStudioEvent.ApplyAssetToSnapshot -> viewModelScope.launch {
                try {
                    ruleWriter(event.snapshot, event.asset)
                } catch (e: Exception) {
                    emitError(UiText(R.string.error_apply_rule_failed))
                    _uiState.value = _uiState.value.copy(applySheet = null)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(applySheet = null)
                _effects.emit(
                    CardStudioEffect.ShowMessage(
                        UiText(R.string.rule_saved_message, listOf(event.snapshot.title)),
                    ),
                )
                refresh()
            }

            is CardStudioEvent.RequestAssetActions -> {
                _uiState.value = _uiState.value.copy(actionsSheet = AssetActionsSheet(event.asset))
            }

            CardStudioEvent.DismissActionsSheet -> {
                _uiState.value = _uiState.value.copy(actionsSheet = null)
            }

            is CardStudioEvent.ConfirmDeleteAsset -> {
                _uiState.value = _uiState.value.copy(
                    pendingDeleteAsset = event.asset,
                    actionsSheet = null,
                )
            }

            CardStudioEvent.DismissDeleteConfirm -> {
                _uiState.value = _uiState.value.copy(pendingDeleteAsset = null)
            }
        }
    }

    private suspend fun refresh() {
        try {
            val assets = assetReader()
            // Query all assignment counts in one pass, then fill missing assets as zero.
            val countsMap = assignmentCounts(assets)
            val counts = assets.associate { it.id to (countsMap[it.id] ?: 0) }
            _uiState.value = _uiState.value.copy(
                assets = assets,
                assignmentCountByAssetId = counts,
                totalAssignments = counts.values.sum(),
                isLoading = false,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            emitError(UiText(R.string.error_load_studio_failed))
        }
    }
}
