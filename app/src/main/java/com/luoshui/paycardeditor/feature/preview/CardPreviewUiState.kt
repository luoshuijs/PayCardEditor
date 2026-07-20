package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.ui.UiText

/**
 * UI model for [CardPreviewScreen].
 *
 * [items] are pre-projected by the ViewModel, so Composables do not
 * synchronously query SharedPreferences while rendering rows. [pendingAction]
 * packages the target snapshot and restore availability together so dialog
 * state changes atomically across refreshes.
 */
internal data class CardPreviewUiState(
    val items: List<CardPreviewItem> = emptyList(),
    val warning: String = "",
    val isLoading: Boolean = true,
    val pendingAction: CardPreviewAction? = null,
)

/**
 * UI projection for one card.
 *
 * The feature layer receives scalar display fields instead of hook-internal
 * rule objects.
 */
internal data class CardPreviewItem(
    val snapshot: CardSnapshot,
    /** Model passed to Coil AsyncImage; null renders the placeholder. */
    val displayImageUrl: String?,
    /** True when a custom rule overrides the default card art. */
    val appliedLabel: Boolean,
)

/**
 * Current actions dialog state.
 *
 * @property snapshot target card
 * @property canRestore true when the current in-memory item table says the card has a custom rule.
 */
internal data class CardPreviewAction(
    val snapshot: CardSnapshot,
    val canRestore: Boolean,
)

/**
 * One-way user event stream from the screen to the ViewModel.
 */
internal sealed interface CardPreviewEvent {
    /** Shared refresh event for resume, pull refresh, and post-restore reloads. */
    data object Refresh : CardPreviewEvent

    /** User clicked a row and requested the actions dialog. */
    data class RequestActions(val snapshot: CardSnapshot) : CardPreviewEvent

    /** User dismissed the actions dialog. */
    data object DismissActions : CardPreviewEvent

    /** User confirmed rule removal for the selected card. */
    data class ConfirmRestore(val snapshot: CardSnapshot) : CardPreviewEvent
}

/**
 * One-shot ViewModel-to-screen effect channel for success messages.
 *
 * It stays separate from `errorEvents` so success and failure feedback can
 * evolve independently without mixing collector semantics.
 */
internal sealed interface CardPreviewEffect {
    /** Success feedback message shown by the screen. */
    data class ShowMessage(val message: UiText) : CardPreviewEffect
}
