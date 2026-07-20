package com.luoshui.paycardeditor.feature.preview

import androidx.lifecycle.viewModelScope
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshot
import com.luoshui.paycardeditor.ui.BaseViewModel
import com.luoshui.paycardeditor.ui.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reader projection that keeps [CardPreviewViewModel] decoupled from the module state repository.
 *
 * Production wiring maps `loadHomeState().cardState` into this structure, while
 * tests can construct it directly.
 */
internal data class CardPreviewStateProjection(
    val cards: List<CardSnapshot>,
    val warning: String,
)

/**
 * Rule lookup projection for one card without exposing hook-internal rule types.
 *
 * @property applied whether a custom rule matches the snapshot
 * @property replacementFace the rule's replacement face value, or an empty string when no rule exists
 */
internal data class CardPreviewRuleInfo(
    val applied: Boolean,
    val replacementFace: String,
) {
    companion object {
        val EMPTY = CardPreviewRuleInfo(applied = false, replacementFace = "")
    }
}

/**
 * State holder for [CardPreviewScreen].
 *
 * [BaseViewModel] supplies [viewModelScope] and error events. Data access is
 * injected through reader and writer lambdas so the ViewModel stays free of
 * Android Context and easy to unit test. [ruleLookup] batches all rule checks
 * for the current snapshots so row rendering never performs synchronous
 * SharedPreferences reads.
 *
 * [ruleRemover] emits success feedback through [effects] only after the remove
 * operation succeeds; failures emit [errorEvents] instead.
 *
 * @param cardStateReader reads the current preview projection.
 * @param ruleLookup batch lookup for each snapshot, keyed by `snapshot.key`.
 * @param imageResolver maps a snapshot and replacement face to an image URL.
 * @param ruleRemover removes the custom rule for a snapshot.
 */
internal class CardPreviewViewModel(
    private val cardStateReader: suspend () -> CardPreviewStateProjection,
    private val ruleLookup: suspend (List<CardSnapshot>) -> Map<String, CardPreviewRuleInfo>,
    private val ruleRemover: suspend (CardSnapshot) -> Unit,
    private val imageResolver: (CardSnapshot, String) -> String? = { _, replaceFace ->
        replaceFace.ifBlank { null }
    },
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(CardPreviewUiState())
    val uiState: StateFlow<CardPreviewUiState> = _uiState.asStateFlow()

    /**
     * One-shot success feedback channel.
     *
     * Restore failures only emit [errorEvents]; a successful restore emits
     * [CardPreviewEffect.ShowMessage]. `replay = 0` prevents configuration
     * changes from replaying old toasts, and `extraBufferCapacity` handles short bursts.
     */
    private val _effects = MutableSharedFlow<CardPreviewEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<CardPreviewEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun handleEvent(event: CardPreviewEvent) {
        when (event) {
            CardPreviewEvent.Refresh -> viewModelScope.launch { refresh() }

            is CardPreviewEvent.RequestActions -> {
                // Pure in-memory lookup; refresh already populated appliedLabel for each item.
                val current = _uiState.value
                val match = current.items.firstOrNull { it.snapshot.key == event.snapshot.key }
                _uiState.value = current.copy(
                    pendingAction = CardPreviewAction(
                        snapshot = event.snapshot,
                        canRestore = match?.appliedLabel == true,
                    ),
                )
            }

            CardPreviewEvent.DismissActions -> {
                _uiState.value = _uiState.value.copy(pendingAction = null)
            }

            is CardPreviewEvent.ConfirmRestore -> viewModelScope.launch {
                try {
                    ruleRemover(event.snapshot)
                } catch (e: Exception) {
                    // Failure path emits only the error toast, avoiding a false success message.
                    emitError(UiText(R.string.error_restore_rule_failed))
                    _uiState.value = _uiState.value.copy(pendingAction = null)
                    return@launch
                }
                // Emit success feedback before refresh so the toast matches the final list update.
                _effects.emit(
                    CardPreviewEffect.ShowMessage(
                        UiText(R.string.rule_restored_message, listOf(event.snapshot.title)),
                    ),
                )
                refresh()
                _uiState.value = _uiState.value.copy(pendingAction = null)
            }
        }
    }

    private suspend fun refresh() {
        try {
            val projection = cardStateReader()
            val rules = ruleLookup(projection.cards)
            val items = projection.cards.map { snapshot ->
                val info = rules[snapshot.key] ?: CardPreviewRuleInfo.EMPTY
                CardPreviewItem(
                    snapshot = snapshot,
                    displayImageUrl = imageResolver(snapshot, info.replacementFace),
                    appliedLabel = info.applied,
                )
            }
            // Preserve pendingAction so refresh does not close a dialog the user is using.
            _uiState.value = _uiState.value.copy(
                items = items,
                warning = projection.warning,
                isLoading = false,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            emitError(UiText(R.string.error_load_preview_failed))
        }
    }
}
