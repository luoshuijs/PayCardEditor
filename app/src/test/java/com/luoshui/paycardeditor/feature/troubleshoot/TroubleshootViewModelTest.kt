package com.luoshui.paycardeditor.feature.troubleshoot

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.TroubleshootState
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pure-JVM tests for [TroubleshootViewModel]. State reads are injected through a
 * lambda, while clipboard and toast work is delivered through [TroubleshootEffect],
 * so no Android Context or Robolectric is needed.
 *
 * [MainDispatcherRule] installs `Dispatchers.Main` so [viewModelScope] launches can
 * run under `runTest`.
 *
 * Coverage includes init refresh, explicit refresh, copy effects, null-state
 * suppression, and reader failure handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TroubleshootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Fakes(
        var state: TroubleshootState = TroubleshootState(
            debugInfo = "fake debug",
            hookMethods = "fake hooks",
            updatedAt = 12345L,
        ),
        var readerShouldThrow: Boolean = false,
    ) {
        var stateReaderCalls: Int = 0
    }

    private fun viewModel(fakes: Fakes): TroubleshootViewModel = TroubleshootViewModel(
        stateReader = {
            fakes.stateReaderCalls += 1
            if (fakes.readerShouldThrow) {
                throw RuntimeException("simulated reader failure")
            }
            fakes.state
        },
    )

    @Test
    fun `init triggers refresh and loads state`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, fakes.stateReaderCalls)
        assertNotNull("troubleshootState should be populated after init refresh", state.troubleshootState)
        assertEquals(fakes.state, state.troubleshootState)
        assertFalse("isLoading should be false after refresh completes", state.isLoading)
    }

    @Test
    fun `Refresh event re-reads state from reader`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()
        val callsAfterInit = fakes.stateReaderCalls

        fakes.state = TroubleshootState(
            debugInfo = "updated debug",
            hookMethods = "updated hooks",
            updatedAt = 67890L,
        )
        vm.handleEvent(TroubleshootEvent.Refresh)
        advanceUntilIdle()

        assertEquals(callsAfterInit + 1, fakes.stateReaderCalls)
        assertEquals(fakes.state, vm.uiState.value.troubleshootState)
        assertFalse(vm.uiState.value.isLoading)
    }

    /**
     * CopyDebug emits [TroubleshootEffect.CopyToClipboard] with debugInfo.
     *
     * `MutableSharedFlow(replay=0, extraBufferCapacity=4)` can buffer an emission
     * when there are no collectors, but a later collector will not replay that
     * buffered value. Subscribe first, advance until the collector is active, then
     * emit the event.
     */
    @Test
    fun `CopyDebug emits CopyToClipboard effect with debugInfo`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val collected = mutableListOf<TroubleshootEffect>()
        val job = launch { vm.effects.toList(collected) }
        advanceUntilIdle() // Let the collector subscribe before emitting.
        vm.handleEvent(TroubleshootEvent.CopyDebug)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, collected.size)
        val effect = collected[0] as TroubleshootEffect.CopyToClipboard
        assertEquals("debug", effect.label)
        assertEquals("fake debug", effect.text)
    }

    @Test
    fun `CopyHooks emits CopyToClipboard effect with hookMethods`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val collected = mutableListOf<TroubleshootEffect>()
        val job = launch { vm.effects.toList(collected) }
        advanceUntilIdle()
        vm.handleEvent(TroubleshootEvent.CopyHooks)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, collected.size)
        val effect = collected[0] as TroubleshootEffect.CopyToClipboard
        assertEquals("hooks", effect.label)
        assertEquals("fake hooks", effect.text)
    }

    /**
     * CopyDebug and CopyHooks emit nothing while state is still null.
     */
    @Test
    fun `Copy events with null state emit nothing`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        // Do not advance yet; init refresh is queued and stateReader has not run.
        assertNull("precondition: state should still be null before dispatcher advances", vm.uiState.value.troubleshootState)

        val collected = mutableListOf<TroubleshootEffect>()
        val job = launch { vm.effects.toList(collected) }
        vm.handleEvent(TroubleshootEvent.CopyDebug)
        vm.handleEvent(TroubleshootEvent.CopyHooks)
        advanceUntilIdle()
        job.cancel()

        assertTrue("no effect should be emitted while state is null", collected.isEmpty())
    }

    /**
     * When `stateReader` throws, [R.string.error_load_troubleshoot_failed] is
     * emitted, `isLoading` returns to false, and the previous state is preserved.
     *
     * `errorEvents` uses `MutableSharedFlow(replay=0)`, so the collector must
     * subscribe before init refresh emits. [CoroutineStart.UNDISPATCHED] enters the
     * collect suspension point immediately, then `advanceUntilIdle()` runs refresh.
     */
    @Test
    fun `Refresh handles stateReader exception emits errorEvents`() = runTest {
        val fakes = Fakes(readerShouldThrow = true)
        val vm = viewModel(fakes)

        val collected = mutableListOf<UiText>()
        val collectorJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.errorEvents.toList(collected)
        }
        advanceUntilIdle()
        collectorJob.cancel()

        assertEquals("应 emit 一个 error 事件", 1, collected.size)
        assertEquals(R.string.error_load_troubleshoot_failed, collected[0].resId)
        // The catch path resets isLoading.
        assertFalse("isLoading 应在 refresh 失败后复位为 false", vm.uiState.value.isLoading)
        // Initial load failed, so no troubleshoot state exists yet.
        assertNull(vm.uiState.value.troubleshootState)
    }
}
