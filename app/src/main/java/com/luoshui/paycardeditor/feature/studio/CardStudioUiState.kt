package com.luoshui.paycardeditor.feature.studio

import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.ui.UiText

/**
 * UI state projection for [CardStudioScreen].
 *
 * [assets] is the sorted asset snapshot. [assignmentCountByAssetId] is
 * precomputed in the ViewModel to avoid repeated SharedPreferences reads during
 * recomposition, and [totalAssignments] is derived from that map. The nullable
 * sheet fields make dialog open state and payload update atomically.
 */
data class CardStudioUiState(
    val assets: List<CardAsset> = emptyList(),
    val assignmentCountByAssetId: Map<String, Int> = emptyMap(),
    val totalAssignments: Int = 0,
    val isLoading: Boolean = true,
    val applySheet: ApplyAssetSheet? = null,
    val actionsSheet: AssetActionsSheet? = null,
    val pendingDeleteAsset: CardAsset? = null,
)

/**
 * Dialog payload for applying one asset to a card.
 *
 * [candidates] contains cards that support custom art plus a precomputed flag
 * for whether each card already has a custom rule.
 */
data class ApplyAssetSheet(
    val asset: CardAsset,
    val candidates: List<ApplyCandidate>,
)

data class ApplyCandidate(
    val snapshot: CardSnapshot,
    val alreadyCustomized: Boolean,
)

/**
 * Dialog payload for long-press asset actions.
 */
data class AssetActionsSheet(
    val asset: CardAsset,
)

/**
 * Events emitted by [CardStudioScreen] and consumed by [CardStudioViewModel].
 *
 * The events cover asset IO, crop results, apply/action/delete dialogs, and a
 * picker marker. [PickImage] is currently handled by the screen launcher path,
 * so the ViewModel treats it as a no-op while retaining one event API.
 */
sealed interface CardStudioEvent {
    data object Refresh : CardStudioEvent
    data object PickImage : CardStudioEvent
    data class CropResult(
        val croppedFile: java.io.File,
        val displayName: String,
        val existingAssetId: String?,
    ) : CardStudioEvent
    data class RemoveAsset(val asset: CardAsset) : CardStudioEvent

    data class RequestApplyAsset(val asset: CardAsset) : CardStudioEvent
    data object DismissApplySheet : CardStudioEvent
    data class ApplyAssetToSnapshot(
        val asset: CardAsset,
        val snapshot: CardSnapshot,
    ) : CardStudioEvent

    data class RequestAssetActions(val asset: CardAsset) : CardStudioEvent
    data object DismissActionsSheet : CardStudioEvent

    data class ConfirmDeleteAsset(val asset: CardAsset) : CardStudioEvent
    data object DismissDeleteConfirm : CardStudioEvent
}

/** One-shot feedback emitted only after a Studio operation reaches its final result. */
sealed interface CardStudioEffect {
    data class ShowMessage(val message: UiText) : CardStudioEffect
}
