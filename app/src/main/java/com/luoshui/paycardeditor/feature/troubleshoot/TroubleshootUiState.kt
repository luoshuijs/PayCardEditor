package com.luoshui.paycardeditor.feature.troubleshoot

import com.luoshui.paycardeditor.model.TroubleshootState

/**
 * UI state projection for the troubleshooting screen.
 *
 * The whole immutable state is replaced on refresh. [troubleshootState] stays
 * null until the first refresh completes, with [isLoading] set to true.
 */
internal data class TroubleshootUiState(
    val troubleshootState: TroubleshootState? = null,
    val isLoading: Boolean = true,
)

/**
 * User events emitted by the screen and consumed by [TroubleshootViewModel].
 *
 * Copy actions are converted into [TroubleshootEffect.CopyToClipboard] so the
 * ViewModel does not hold Android Context or Clipboard APIs.
 */
internal sealed interface TroubleshootEvent {
    data object Refresh : TroubleshootEvent
    data object CopyDebug : TroubleshootEvent
    data object CopyHooks : TroubleshootEvent
}

/**
 * One-shot side effects emitted by the ViewModel.
 *
 * Clipboard writes are effects instead of state so tests can assert the emitted
 * text while Android clipboard APIs stay in the screen layer.
 *
 * @property label label used for the primary clip
 * @property text plain text written to the Clipboard
 */
internal sealed interface TroubleshootEffect {
    data class CopyToClipboard(val label: String, val text: String) : TroubleshootEffect
}
