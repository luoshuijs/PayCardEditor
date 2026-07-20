package com.luoshui.paycardeditor.feature.home

import androidx.lifecycle.viewModelScope
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.HomeState
import com.luoshui.paycardeditor.ui.BaseViewModel
import com.luoshui.paycardeditor.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for [HomeScreen].
 *
 * [BaseViewModel] supplies [viewModelScope] and error events. Data access is
 * injected through lambdas so the ViewModel stays free of Android Context and
 * simple to unit test. [serviceConnectedFlow] is injected from the app process
 * service state. `uiState` is eagerly shared so short collection gaps, such as
 * tab changes, keep the latest state.
 *
 * @param homeStateLoader loads the latest [HomeState].
 * @param syncTrigger runs the sync action before reloading [homeStateLoader].
 * The hook process writes the shared `paycardeditor_state`; the UI can only
 * reread the snapshot.
 * @param serviceConnectedFlow current libxposed service connection state.
 */
class HomeViewModel(
    private val homeStateLoader: suspend () -> HomeState,
    private val syncTrigger: suspend () -> Unit,
    serviceConnectedFlow: StateFlow<Boolean>,
) : BaseViewModel() {

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            serviceConnectedFlow.collect { refresh() }
        }
    }

    fun handleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.SyncSnapshots -> viewModelScope.launch {
                try {
                    syncTrigger()
                } catch (e: Exception) {
                    emitError(UiText(R.string.error_sync_home_failed))
                    return@launch
                }
                refresh(showSnapshotDetails = true)
            }
            HomeEvent.Refresh -> viewModelScope.launch { refresh() }
            HomeEvent.DismissSnapshotDetails -> {
                mutableUiState.value = mutableUiState.value.copy(snapshotDetails = null)
            }
            // Route lambdas handle navigation because the ViewModel has no Activity.
            HomeEvent.OpenTroubleshoot -> Unit
            HomeEvent.OpenMiPay -> Unit
        }
    }

    private suspend fun refresh(showSnapshotDetails: Boolean = false) {
        try {
            val homeState = homeStateLoader()
            mutableUiState.value = mutableUiState.value.copy(
                homeState = homeState,
                snapshotDetails = if (showSnapshotDetails) {
                    homeState
                } else {
                    mutableUiState.value.snapshotDetails
                },
            )
        } catch (e: Exception) {
            // Keep the last successful snapshot so a transient error does not blank the screen.
            emitError(UiText(R.string.error_load_home_failed))
        }
    }
}
