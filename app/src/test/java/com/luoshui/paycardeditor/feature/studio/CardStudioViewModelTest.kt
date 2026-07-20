package com.luoshui.paycardeditor.feature.studio

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.CardAsset
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pure-JVM tests for [CardStudioViewModel]. All data access is injected through
 * reader/writer lambdas, so no Android Context or Robolectric is needed.
 *
 * [MainDispatcherRule] installs `Dispatchers.Main` so [viewModelScope] launches can
 * run under `runTest`.
 *
 * Coverage includes init refresh, asset removal, crop save results, apply-sheet
 * candidate state, rule writes, action/delete dialog state, PickImage no-op behavior,
 * failure handling, and batched assignment counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardStudioViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeAsset(id: String = "a1", name: String = "卡面1", fileName: String = "a1.png") =
        CardAsset(id = id, displayName = name, fileName = fileName, updatedAt = 1_000L)

    private fun makeSnapshot(
        cardName: String = "测试银行卡",
        cardType: String = "BANK_CARD",
    ): CardSnapshot = CardSnapshot(cardName = cardName, cardType = cardType, isBankCard = true)

    /**
     * Default-passthrough fakes record side-effect calls and arguments so each test
     * can configure only the behavior it cares about.
     */
    private class Fakes(
        var assets: List<CardAsset> = emptyList(),
        var assignmentCounts: Map<String, Int> = emptyMap(),
        var snapshots: List<CardSnapshot> = emptyList(),
        var customizedSnapshots: Set<CardSnapshot> = emptySet(),
        var deleterShouldThrow: Boolean = false,
    ) {
        var saverCalls = 0
        var lastSavedDisplayName: String? = null
        var lastSavedExistingId: String? = null
        var deleterCalls = 0
        var lastDeletedAssetId: String? = null
        var rulesRemoverCalls = 0
        var ruleWriterCalls = 0
        var lastRuleAsset: CardAsset? = null
        var lastRuleSnapshot: CardSnapshot? = null
        var customizedReaderCalls = 0

        // Batch reader call count; each refresh should add one call regardless of asset count.
        var assignmentCountsCalls = 0
        var lastAssignmentCountsArgSize: Int? = null
    }

    private fun viewModel(fakes: Fakes): CardStudioViewModel = CardStudioViewModel(
        assetReader = { fakes.assets },
        assignmentCounts = { assets ->
            fakes.assignmentCountsCalls += 1
            fakes.lastAssignmentCountsArgSize = assets.size
            // Default fake behavior returns a complete map, treating missing assets as zero.
            assets.associate { it.id to (fakes.assignmentCounts[it.id] ?: 0) }
        },
        snapshotsReader = { fakes.snapshots },
        assetSaver = { _, name, existingId ->
            fakes.saverCalls += 1
            fakes.lastSavedDisplayName = name
            fakes.lastSavedExistingId = existingId
            // Return a new asset with a later timestamp to simulate a refreshed version.
            CardAsset(
                id = existingId ?: "new-${fakes.saverCalls}",
                displayName = name,
                fileName = "${existingId ?: "new-${fakes.saverCalls}"}.png",
                updatedAt = 2_000L,
            )
        },
        assetDeleter = { id ->
            fakes.deleterCalls += 1
            fakes.lastDeletedAssetId = id
            if (fakes.deleterShouldThrow) {
                throw RuntimeException("simulated deleter failure")
            }
            fakes.assets.firstOrNull { it.id == id }
        },
        rulesForAssetRemover = { _ -> fakes.rulesRemoverCalls += 1 },
        ruleWriter = { snap, asset ->
            fakes.ruleWriterCalls += 1
            fakes.lastRuleSnapshot = snap
            fakes.lastRuleAsset = asset
        },
        customizedReader = { snap ->
            fakes.customizedReaderCalls += 1
            snap in fakes.customizedSnapshots
        },
    )

    @Test
    fun `init triggers refresh and populates assets + assignment counts`() = runTest {
        val asset1 = makeAsset(id = "a1")
        val asset2 = makeAsset(id = "a2", name = "卡面2", fileName = "a2.png")
        val fakes = Fakes(
            assets = listOf(asset1, asset2),
            assignmentCounts = mapOf("a1" to 3, "a2" to 0),
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(listOf(asset1, asset2), state.assets)
        assertEquals(mapOf("a1" to 3, "a2" to 0), state.assignmentCountByAssetId)
        assertEquals(3, state.totalAssignments)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `RemoveAsset calls deleter and rulesRemover then refreshes`() = runTest {
        val asset = makeAsset(id = "del-1")
        val fakes = Fakes(assets = listOf(asset))
        val vm = viewModel(fakes)
        advanceUntilIdle()

        vm.handleEvent(CardStudioEvent.RemoveAsset(asset))
        advanceUntilIdle()
        assertEquals(1, fakes.deleterCalls)
        assertEquals("del-1", fakes.lastDeletedAssetId)
        assertEquals(1, fakes.rulesRemoverCalls)
    }

    @Test
    fun `CropResult calls saver and refreshes`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val tempFile = File.createTempFile("crop-result-", ".png").also { it.deleteOnExit() }
        vm.handleEvent(
            CardStudioEvent.CropResult(
                croppedFile = tempFile,
                displayName = "新卡面",
                existingAssetId = null,
            )
        )
        advanceUntilIdle()
        assertEquals(1, fakes.saverCalls)
        assertEquals("新卡面", fakes.lastSavedDisplayName)
        assertNull(fakes.lastSavedExistingId)
    }

    @Test
    fun `CropResult with existingAssetId propagates id to saver`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()
        val tempFile = File.createTempFile("crop-edit-", ".png").also { it.deleteOnExit() }
        vm.handleEvent(
            CardStudioEvent.CropResult(
                croppedFile = tempFile,
                displayName = "覆盖名",
                existingAssetId = "asset-99",
            )
        )
        advanceUntilIdle()
        assertEquals("asset-99", fakes.lastSavedExistingId)
    }

    @Test
    fun `RequestApplyAsset with non-empty snapshots populates applySheet`() = runTest {
        val asset = makeAsset()
        val fakes = Fakes(snapshots = listOf(makeSnapshot(), makeSnapshot(cardName = "另一张")))
        val vm = viewModel(fakes)
        advanceUntilIdle()

        vm.handleEvent(CardStudioEvent.RequestApplyAsset(asset))
        advanceUntilIdle()
        val sheet = vm.uiState.value.applySheet
        assertNotNull(sheet)
        assertSame(asset, sheet!!.asset)
        assertEquals(2, sheet.candidates.size)
    }

    /**
     * `RequestApplyAsset` must call `customizedReader` in the event coroutine to
     * compute each candidate's `alreadyCustomized` field.
     */
    @Test
    fun `RequestApplyAsset populates alreadyCustomized from customizedReader for each candidate`() = runTest {
        val asset = makeAsset()
        val customized = makeSnapshot(cardName = "已设卡面")
        val plain = makeSnapshot(cardName = "默认卡面")
        val fakes = Fakes(
            snapshots = listOf(customized, plain),
            customizedSnapshots = setOf(customized),
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()

        vm.handleEvent(CardStudioEvent.RequestApplyAsset(asset))
        advanceUntilIdle()

        val sheet = vm.uiState.value.applySheet
        assertNotNull(sheet)
        assertEquals(2, sheet!!.candidates.size)
        assertEquals(2, fakes.customizedReaderCalls)

        val byName = sheet.candidates.associateBy { it.snapshot.cardName }
        assertTrue(
            "已设卡面候选的 alreadyCustomized 应为 true",
            byName.getValue("已设卡面").alreadyCustomized,
        )
        assertFalse(
            "默认卡面候选的 alreadyCustomized 应为 false",
            byName.getValue("默认卡面").alreadyCustomized,
        )
    }

    @Test
    fun `RequestApplyAsset with empty snapshots leaves applySheet null`() = runTest {
        val asset = makeAsset()
        val fakes = Fakes(snapshots = emptyList())
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RequestApplyAsset(asset))
        advanceUntilIdle()
        assertNull(vm.uiState.value.applySheet)
    }

    @Test
    fun `DismissApplySheet clears applySheet`() = runTest {
        val asset = makeAsset()
        val fakes = Fakes(snapshots = listOf(makeSnapshot()))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RequestApplyAsset(asset))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.applySheet)
        vm.handleEvent(CardStudioEvent.DismissApplySheet)
        assertNull(vm.uiState.value.applySheet)
    }

    @Test
    fun `ApplyAssetToSnapshot invokes ruleWriter and clears applySheet`() = runTest {
        val asset = makeAsset()
        val snapshot = makeSnapshot()
        val fakes = Fakes(snapshots = listOf(snapshot))
        val vm = viewModel(fakes)
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RequestApplyAsset(asset))
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.ApplyAssetToSnapshot(asset, snapshot))
        advanceUntilIdle()
        assertEquals(1, fakes.ruleWriterCalls)
        assertSame(asset, fakes.lastRuleAsset)
        assertSame(snapshot, fakes.lastRuleSnapshot)
        assertNull(vm.uiState.value.applySheet)
    }

    @Test
    fun `RequestAssetActions sets actionsSheet then DismissActionsSheet clears it`() = runTest {
        val asset = makeAsset()
        val vm = viewModel(Fakes())
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RequestAssetActions(asset))
        assertNotNull(vm.uiState.value.actionsSheet)
        assertSame(asset, vm.uiState.value.actionsSheet!!.asset)
        vm.handleEvent(CardStudioEvent.DismissActionsSheet)
        assertNull(vm.uiState.value.actionsSheet)
    }

    @Test
    fun `ConfirmDeleteAsset sets pending and clears actionsSheet, Dismiss clears pending`() = runTest {
        val asset = makeAsset()
        val vm = viewModel(Fakes())
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RequestAssetActions(asset))
        vm.handleEvent(CardStudioEvent.ConfirmDeleteAsset(asset))
        val s = vm.uiState.value
        assertSame(asset, s.pendingDeleteAsset)
        assertNull(s.actionsSheet)
        vm.handleEvent(CardStudioEvent.DismissDeleteConfirm)
        assertNull(vm.uiState.value.pendingDeleteAsset)
    }

    @Test
    fun `PickImage is no-op in ViewModel`() = runTest {
        val fakes = Fakes()
        val vm = viewModel(fakes)
        advanceUntilIdle()
        val before = vm.uiState.value
        vm.handleEvent(CardStudioEvent.PickImage)
        advanceUntilIdle()
        // No side effects occur; call counts stay zero and the state reference is unchanged.
        assertEquals(0, fakes.saverCalls)
        assertEquals(0, fakes.deleterCalls)
        assertEquals(0, fakes.ruleWriterCalls)
        assertSame(before, vm.uiState.value)
    }

    @Test
    fun `Refresh recomputes assets and totalAssignments after external mutation`() = runTest {
        val a1 = makeAsset(id = "a1")
        val a2 = makeAsset(id = "a2", name = "卡面2", fileName = "a2.png")
        val fakes = Fakes(
            assets = listOf(a1),
            assignmentCounts = mapOf("a1" to 1),
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.totalAssignments)

        // Simulate an external mutation adding another asset.
        fakes.assets = listOf(a1, a2)
        fakes.assignmentCounts = mapOf("a1" to 1, "a2" to 4)
        vm.handleEvent(CardStudioEvent.Refresh)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.assets.size)
        assertEquals(5, vm.uiState.value.totalAssignments)
        assertTrue(vm.uiState.value.assignmentCountByAssetId.containsKey("a2"))
    }

    /**
     * When `assetDeleter` throws, [R.string.error_remove_asset_failed] is emitted and
     * refresh is skipped, so `rulesRemover` is not called.
     */
    @Test
    fun `RemoveAsset handles deleter exception emits errorEvents`() = runTest {
        val asset = makeAsset(id = "del-fail")
        val fakes = Fakes(assets = listOf(asset), deleterShouldThrow = true)
        val vm = viewModel(fakes)
        advanceUntilIdle()

        val collected = mutableListOf<UiText>()
        val job = launch { vm.errorEvents.toList(collected) }
        advanceUntilIdle()
        vm.handleEvent(CardStudioEvent.RemoveAsset(asset))
        advanceUntilIdle()
        job.cancel()

        assertEquals("应 emit 一个 error 事件", 1, collected.size)
        assertEquals(R.string.error_remove_asset_failed, collected[0].resId)
        // The catch path returns before rulesRemover can run.
        assertEquals(0, fakes.rulesRemoverCalls)
    }

    /**
     * `assignmentCounts` is a batch reader and must be called once per refresh
     * regardless of asset count.
     *
     * This prevents a per-asset reader from reloading rules for every asset and
     * keeps refresh work O(1) with respect to rule-store reads.
     */
    @Test
    fun `init populates assignmentCounts with single batch call`() = runTest {
        val assets = listOf(
            makeAsset(id = "a1"),
            makeAsset(id = "a2", name = "卡面2", fileName = "a2.png"),
            makeAsset(id = "a3", name = "卡面3", fileName = "a3.png"),
            makeAsset(id = "a4", name = "卡面4", fileName = "a4.png"),
            makeAsset(id = "a5", name = "卡面5", fileName = "a5.png"),
        )
        val fakes = Fakes(
            assets = assets,
            assignmentCounts = mapOf("a1" to 1, "a2" to 2, "a3" to 0, "a4" to 3, "a5" to 0),
        )
        val vm = viewModel(fakes)
        advanceUntilIdle()

        // Five assets should trigger one batch reader call, not one call per asset.
        assertEquals("init 应仅触发 1 次批量 assignmentCounts 调用", 1, fakes.assignmentCountsCalls)
        assertEquals("批量 reader 应收到完整 asset 列表", 5, fakes.lastAssignmentCountsArgSize)
        assertEquals(6, vm.uiState.value.totalAssignments)

        // A second refresh adds one more batch call.
        vm.handleEvent(CardStudioEvent.Refresh)
        advanceUntilIdle()
        assertEquals("Refresh 后批量调用累计 = 2 次", 2, fakes.assignmentCountsCalls)
    }
}
