package com.luoshui.paycardeditor.feature.preview

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.model.CardSnapshot
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
 * Pure-JVM tests for [CardPreviewViewModel]. All data access is injected through
 * reader/writer lambdas, so no Android Context or Robolectric is needed.
 *
 * [MainDispatcherRule] installs `Dispatchers.Main` so [viewModelScope] launches can
 * run under `runTest`.
 *
 * Coverage includes init refresh, batch rule lookup, actions sheet state,
 * restore success and failure events, external mutation refresh, and unknown
 * snapshot fallback behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardPreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeSnapshot(
        cardName: String,
        cardType: String = "BANK_CARD",
        panLastDigits: String = "",
    ): CardSnapshot = CardSnapshot(
        cardName = cardName,
        cardType = cardType,
        panLastDigits = panLastDigits,
        isBankCard = true,
    )

    /**
     * Wraps side-effect and data-access lambdas so each test can configure only
     * the fields it needs.
     */
    private class Fakes(
        var cards: List<CardSnapshot> = emptyList(),
        var warning: String = "",
        /** Snapshot keys treated as customized by rule lookup. */
        var customizedKeys: Set<String> = emptySet(),
        /** Replacement face value returned by customized rules. */
        var replacementFace: String = "https://example.test/face.png",
        var ruleRemoverShouldThrow: Boolean = false,
    ) {
        var ruleLookupCalls: Int = 0
        var ruleRemoverCalls: MutableList<CardSnapshot> = mutableListOf()
        var imageResolverCalls: MutableList<Pair<CardSnapshot, String>> = mutableListOf()
    }

    private fun viewModel(fakes: Fakes): CardPreviewViewModel = CardPreviewViewModel(
        cardStateReader = {
            CardPreviewStateProjection(cards = fakes.cards, warning = fakes.warning)
        },
        ruleLookup = { snapshots ->
            fakes.ruleLookupCalls += 1
            snapshots.associate { snap ->
                snap.key to if (snap.key in fakes.customizedKeys) {
                    CardPreviewRuleInfo(applied = true, replacementFace = fakes.replacementFace)
                } else {
                    CardPreviewRuleInfo.EMPTY
                }
            }
        },
        ruleRemover = { snapshot ->
            fakes.ruleRemoverCalls += snapshot
            if (fakes.ruleRemoverShouldThrow) {
                throw RuntimeException("simulated rule remover failure")
            }
            // Simulate restoring defaults by removing this snapshot from customized rules.
            fakes.customizedKeys = fakes.customizedKeys - snapshot.key
        },
        imageResolver = { snap, replaceFace ->
            fakes.imageResolverCalls += (snap to replaceFace)
            if (replaceFace.isNotEmpty()) replaceFace
            else if (snap.title.isNotBlank()) "snapshot://${snap.title}"
            else null
        },
    )

    @Test
    fun `init triggers refresh and populates items with batch ruleLookup`() = runTest {
        val s1 = makeSnapshot("已自定义卡", panLastDigits = "1234")
        val s2 = makeSnapshot("默认卡", panLastDigits = "5678")
        val fakes = Fakes(
            cards = listOf(s1, s2),
            warning = "",
            customizedKeys = setOf(s1.key),
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(s1.key, state.items[0].snapshot.key)
        assertEquals(s2.key, state.items[1].snapshot.key)
        assertTrue("s1 已自定义", state.items[0].appliedLabel)
        assertFalse("s2 默认", state.items[1].appliedLabel)
        assertEquals("ruleLookup 必须批量预查询，仅 1 次调用（P5b finding 9c）", 1, fakes.ruleLookupCalls)
        assertEquals(2, fakes.imageResolverCalls.size)
        assertEquals("https://example.test/face.png", state.items[0].displayImageUrl)
        assertEquals("snapshot://默认卡", state.items[1].displayImageUrl)
        assertFalse("isLoading 应在 refresh 完成后翻为 false", state.isLoading)
    }

    @Test
    fun `RequestActions sets canRestore true for customized snapshot`() = runTest {
        val s = makeSnapshot("自定义", panLastDigits = "9999")
        val fakes = Fakes(cards = listOf(s), customizedKeys = setOf(s.key))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        val lookupCallsAfterInit = fakes.ruleLookupCalls

        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()

        val action = vm.uiState.value.pendingAction
        assertNotNull("pendingAction 应被设置", action)
        assertEquals(s.key, action!!.snapshot.key)
        assertTrue("已自定义 snapshot canRestore 必为 true", action.canRestore)
        assertEquals(
            "RequestActions 不应触发额外 IO",
            lookupCallsAfterInit, fakes.ruleLookupCalls,
        )
    }

    @Test
    fun `RequestActions sets canRestore false for default snapshot`() = runTest {
        val s = makeSnapshot("默认", panLastDigits = "0000")
        val fakes = Fakes(cards = listOf(s), customizedKeys = emptySet())
        val vm = viewModel(fakes)
        advanceUntilIdle()

        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()

        val action = vm.uiState.value.pendingAction
        assertNotNull(action)
        assertFalse("默认 snapshot canRestore 必为 false", action!!.canRestore)
    }

    @Test
    fun `DismissActions clears pendingAction`() = runTest {
        val s = makeSnapshot("any")
        val fakes = Fakes(cards = listOf(s))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingAction)

        vm.handleEvent(CardPreviewEvent.DismissActions)
        assertNull(vm.uiState.value.pendingAction)
    }

    @Test
    fun `ConfirmRestore calls ruleRemover then refreshes and clears pendingAction`() = runTest {
        val s = makeSnapshot("待恢复", panLastDigits = "1111")
        val fakes = Fakes(cards = listOf(s), customizedKeys = setOf(s.key))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items[0].appliedLabel)
        val lookupCallsBefore = fakes.ruleLookupCalls

        vm.handleEvent(CardPreviewEvent.ConfirmRestore(s))
        advanceUntilIdle()

        assertEquals(1, fakes.ruleRemoverCalls.size)
        assertEquals(s.key, fakes.ruleRemoverCalls[0].key)
        assertEquals(
            "ConfirmRestore 应触发 refresh，ruleLookup 再调 1 次",
            lookupCallsBefore + 1, fakes.ruleLookupCalls,
        )
        assertNull(vm.uiState.value.pendingAction)
        assertFalse(vm.uiState.value.items[0].appliedLabel)
    }

    @Test
    fun `Refresh recomputes items after external rule mutation`() = runTest {
        val s = makeSnapshot("外部变化", panLastDigits = "2222")
        val fakes = Fakes(cards = listOf(s), customizedKeys = emptySet())
        val vm = viewModel(fakes)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.items[0].appliedLabel)

        fakes.customizedKeys = setOf(s.key)
        vm.handleEvent(CardPreviewEvent.Refresh)
        advanceUntilIdle()

        assertTrue(
            "外部规则注入后，Refresh 应让 appliedLabel 翻为 true",
            vm.uiState.value.items[0].appliedLabel,
        )
    }

    @Test
    fun `RequestActions for unknown snapshot falls back to canRestore false`() = runTest {
        val known = makeSnapshot("已知", panLastDigits = "AAAA")
        val unknown = makeSnapshot("未知", panLastDigits = "BBBB")
        val fakes = Fakes(cards = listOf(known), customizedKeys = setOf(known.key))
        val vm = viewModel(fakes)
        advanceUntilIdle()

        vm.handleEvent(CardPreviewEvent.RequestActions(unknown))
        advanceUntilIdle()
        val action = vm.uiState.value.pendingAction
        assertNotNull(action)
        assertEquals(unknown.key, action!!.snapshot.key)
        assertFalse("未知 snapshot 必须 fallback 到 canRestore=false", action.canRestore)
    }

    /**
     * When `ruleRemover` throws, [R.string.error_restore_rule_failed] is emitted and
     * `pendingAction` is cleared so the dialog cannot remain stuck.
     *
     * The failure path must not emit [CardPreviewEffect.ShowMessage], which would
     * duplicate the error toast.
     */
    @Test
    fun `ConfirmRestore handles ruleRemover exception emits errorEvents`() = runTest {
        val s = makeSnapshot("恢复失败", panLastDigits = "9999")
        val fakes = Fakes(
            cards = listOf(s),
            customizedKeys = setOf(s.key),
            ruleRemoverShouldThrow = true,
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingAction)

        val collectedErrors = mutableListOf<UiText>()
        val collectedEffects = mutableListOf<CardPreviewEffect>()
        val errorJob = launch { vm.errorEvents.toList(collectedErrors) }
        val effectJob = launch { vm.effects.toList(collectedEffects) }
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.ConfirmRestore(s))
        advanceUntilIdle()
        errorJob.cancel()
        effectJob.cancel()

        assertEquals("应 emit 一个 error 事件", 1, collectedErrors.size)
        assertEquals(R.string.error_restore_rule_failed, collectedErrors[0].resId)
        assertTrue(
            "失败路径必须**不**emit ShowMessage effect（F4 修复 — 避免双 Toast）",
            collectedEffects.isEmpty(),
        )
        // The catch path clears pendingAction.
        assertNull(vm.uiState.value.pendingAction)
    }

    /**
     * Successful restore emits [CardPreviewEffect.ShowMessage] with
     * [R.string.rule_restored_message] and `snapshot.title` for screen-side toast
     * rendering.
     */
    @Test
    fun `ConfirmRestore success emits ShowMessage effect with snapshot title`() = runTest {
        val s = makeSnapshot("成功恢复", panLastDigits = "1234")
        val fakes = Fakes(cards = listOf(s), customizedKeys = setOf(s.key))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.RequestActions(s))
        advanceUntilIdle()

        val collectedErrors = mutableListOf<UiText>()
        val collectedEffects = mutableListOf<CardPreviewEffect>()
        val errorJob = launch { vm.errorEvents.toList(collectedErrors) }
        val effectJob = launch { vm.effects.toList(collectedEffects) }
        advanceUntilIdle()
        vm.handleEvent(CardPreviewEvent.ConfirmRestore(s))
        advanceUntilIdle()
        errorJob.cancel()
        effectJob.cancel()

        assertEquals("成功路径必须**不**emit error 事件", 0, collectedErrors.size)
        assertEquals("成功路径应 emit 一个 ShowMessage effect", 1, collectedEffects.size)
        val effect = collectedEffects[0]
        assertTrue("effect 类型应为 ShowMessage", effect is CardPreviewEffect.ShowMessage)
        val showMessage = effect as CardPreviewEffect.ShowMessage
        assertEquals(R.string.rule_restored_message, showMessage.message.resId)
        assertEquals(
            "格式化参数应携带 snapshot.title 以便 Screen 端 getString(resId, title) 渲染",
            listOf<Any>(s.title), showMessage.message.args,
        )
        // The success path also clears pendingAction.
        assertNull(vm.uiState.value.pendingAction)
    }
}
