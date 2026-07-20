package com.luoshui.paycardeditor.feature.home

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshotState
import com.luoshui.paycardeditor.model.HomeState
import com.luoshui.paycardeditor.model.ModuleStatusLevel
import com.luoshui.paycardeditor.model.ModuleStatusState
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pure-JVM tests for [HomeViewModel]. State loading and sync are injected through
 * lambdas, so the tests do not need Android Context or Robolectric.
 *
 * [MainDispatcherRule] installs `Dispatchers.Main` so [viewModelScope] launches can
 * run under `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun fakeHomeState(
        level: ModuleStatusLevel = ModuleStatusLevel.WAITING,
        cardCount: Int = 0,
    ): HomeState = HomeState(
        moduleStatus = ModuleStatusState(
            level = level,
            title = "等待连接",
            detail = "尚未连接到 libxposed 服务。",
        ),
        cardState = CardSnapshotState(
            cards = emptyList(),
            lastUpdated = if (cardCount > 0) 1L else 0L,
            lastSource = if (cardCount > 0) "fake" else "",
            warning = "",
        ),
    )

    @Test
    fun `init triggers refresh and loads HomeState`() = runTest {
        val fake = fakeHomeState(level = ModuleStatusLevel.ACTIVE)
        val connected = MutableStateFlow(false)
        val vm = HomeViewModel(
            homeStateLoader = { fake },
            syncTrigger = {},
            serviceConnectedFlow = connected,
        )
        advanceUntilIdle()
        val state = vm.uiState.first()
        assertEquals(fake, state.homeState)
    }

    @Test
    fun `service bind and disconnect reload module status`() = runTest {
        val connected = MutableStateFlow(false)
        var loadCalls = 0
        val vm = HomeViewModel(
            homeStateLoader = {
                loadCalls += 1
                fakeHomeState(
                    level = if (connected.value) {
                        ModuleStatusLevel.ACTIVE
                    } else {
                        ModuleStatusLevel.WAITING
                    },
                )
            },
            syncTrigger = {},
            serviceConnectedFlow = connected,
        )
        advanceUntilIdle()
        assertEquals(ModuleStatusLevel.WAITING, vm.uiState.value.homeState?.moduleStatus?.level)

        connected.value = true
        advanceUntilIdle()

        assertEquals(2, loadCalls)
        assertEquals(ModuleStatusLevel.ACTIVE, vm.uiState.value.homeState?.moduleStatus?.level)

        connected.value = false
        advanceUntilIdle()

        assertEquals(3, loadCalls)
        assertEquals(ModuleStatusLevel.WAITING, vm.uiState.value.homeState?.moduleStatus?.level)
    }

    @Test
    fun `SyncSnapshots invokes syncTrigger then refreshes loader`() = runTest {
        var syncCalls = 0
        var loadCalls = 0
        val vm = HomeViewModel(
            homeStateLoader = {
                loadCalls += 1
                fakeHomeState()
            },
            syncTrigger = { syncCalls += 1 },
            serviceConnectedFlow = MutableStateFlow(false),
        )
        advanceUntilIdle()
        val loadsAfterInit = loadCalls
        assertEquals(0, syncCalls)
        vm.handleEvent(HomeEvent.SyncSnapshots)
        advanceUntilIdle()
        assertEquals(1, syncCalls)
        assertEquals(loadsAfterInit + 1, loadCalls)
    }

    @Test
    fun `Refresh invokes loader without syncTrigger`() = runTest {
        var syncCalls = 0
        var loadCalls = 0
        val vm = HomeViewModel(
            homeStateLoader = {
                loadCalls += 1
                fakeHomeState()
            },
            syncTrigger = { syncCalls += 1 },
            serviceConnectedFlow = MutableStateFlow(false),
        )
        advanceUntilIdle()
        val loadsAfterInit = loadCalls
        vm.handleEvent(HomeEvent.Refresh)
        advanceUntilIdle()
        assertEquals(0, syncCalls)
        assertEquals(loadsAfterInit + 1, loadCalls)
    }

    @Test
    fun `OpenTroubleshoot and OpenMiPay are no-ops in ViewModel`() = runTest {
        var syncCalls = 0
        var loadCalls = 0
        val vm = HomeViewModel(
            homeStateLoader = {
                loadCalls += 1
                fakeHomeState()
            },
            syncTrigger = { syncCalls += 1 },
            serviceConnectedFlow = MutableStateFlow(false),
        )
        advanceUntilIdle()
        val loadsAfterInit = loadCalls
        vm.handleEvent(HomeEvent.OpenTroubleshoot)
        vm.handleEvent(HomeEvent.OpenMiPay)
        advanceUntilIdle()
        assertEquals(0, syncCalls)
        // No additional refresh triggered; Open* events are handled at the Screen layer.
        assertEquals(loadsAfterInit, loadCalls)
    }

    /**
     * When `homeStateLoader` throws during init, [R.string.error_load_home_failed]
     * is emitted and the previous home state is preserved.
     *
     * `BaseViewModel.errorEvents` is a `MutableSharedFlow(replay=0)`, so it does not
     * replay earlier emissions. The collector must subscribe before init refresh
     * emits; [CoroutineStart.UNDISPATCHED] starts collection synchronously, then
     * `advanceUntilIdle()` lets the queued init refresh run.
     */
    @Test
    fun `init handles homeStateLoader exception emits errorEvents`() = runTest {
        // The errorEvents flow is a ViewModel member, so the ViewModel must be
        // constructed first. Its init launch remains queued until the dispatcher is
        // advanced, giving the undispatched collector time to subscribe.
        val vm = HomeViewModel(
            homeStateLoader = { throw RuntimeException("boom") },
            syncTrigger = {},
            serviceConnectedFlow = MutableStateFlow(false),
        )
        val collected = mutableListOf<UiText>()
        val collectorJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.errorEvents.toList(collected)
        }
        advanceUntilIdle()
        collectorJob.cancel()

        assertEquals("应 emit 一个 error 事件", 1, collected.size)
        assertEquals(R.string.error_load_home_failed, collected[0].resId)
        // A failed refresh leaves the previous home state untouched.
        assertEquals(null, vm.uiState.value.homeState)
    }
}
