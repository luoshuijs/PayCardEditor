package com.luoshui.paycardeditor.feature.troubleshoot

import androidx.lifecycle.viewModelScope
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.TroubleshootState
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
 * State holder for [TroubleshootScreen].
 *
 * [BaseViewModel] supplies [viewModelScope] and error events. Data access is
 * injected through [stateReader] so the ViewModel stays free of Android Context.
 * Clipboard and Toast work is delivered through [effects] and performed by the
 * screen layer. The constructor starts the initial refresh; the Activity can
 * dispatch [TroubleshootEvent.Refresh] again on resume.
 *
 * @param stateReader reads the current [TroubleshootState].
 */
internal class TroubleshootViewModel(
    private val stateReader: suspend () -> TroubleshootState,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(TroubleshootUiState())
    val uiState: StateFlow<TroubleshootUiState> = _uiState.asStateFlow()

    // Buffer short click bursts without replaying old copy operations after recomposition.
    private val _effects = MutableSharedFlow<TroubleshootEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<TroubleshootEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun handleEvent(event: TroubleshootEvent) {
        when (event) {
            TroubleshootEvent.Refresh -> viewModelScope.launch { refresh() }

            TroubleshootEvent.CopyDebug -> {
                val text = _uiState.value.troubleshootState?.debugInfo ?: return
                // Drop excess clicks if the small effect buffer is full.
                _effects.tryEmit(TroubleshootEffect.CopyToClipboard(label = "debug", text = text))
            }

            TroubleshootEvent.CopyHooks -> {
                val text = _uiState.value.troubleshootState?.hookMethods ?: return
                _effects.tryEmit(TroubleshootEffect.CopyToClipboard(label = "hooks", text = text))
            }
        }
    }

    private suspend fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            val state = stateReader()
            _uiState.value = TroubleshootUiState(troubleshootState = state, isLoading = false)
        } catch (e: Exception) {
            // Keep the last successful state so a transient error does not blank the screen.
            _uiState.value = _uiState.value.copy(isLoading = false)
            emitError(UiText(R.string.error_load_troubleshoot_failed))
        }
    }
}
