package com.luoshui.paycardeditor.feature.home

import com.luoshui.paycardeditor.model.HomeState

/**
 * Immutable UI projection for [HomeScreen].
 *
 * [homeState] is null until the first load completes. Module status and snapshot
 * details come from the same repository projection so the screen presents one
 * coherent status. This type does not hold Android resources or [android.content.Context].
 */
data class HomeUiState(
    val homeState: HomeState? = null,
)

/**
 * User events emitted by [HomeScreen].
 *
 * [OpenTroubleshoot] and [OpenMiPay] are handled by route lambdas because the
 * ViewModel does not own an Activity or Intent. They remain in the event type
 * so the screen can expose one event callback.
 */
sealed interface HomeEvent {
    data object SyncSnapshots : HomeEvent
    data object Refresh : HomeEvent
    data object OpenTroubleshoot : HomeEvent
    data object OpenMiPay : HomeEvent
}
