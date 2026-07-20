package com.luoshui.paycardeditor.feature.settings

import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode
import com.luoshui.paycardeditor.data.settings.SettingsRepository
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pure-JVM tests for [SettingsViewModel]. Crop config access is injected through
 * reader/writer lambdas, so no Android Context or Robolectric is needed.
 *
 * [MainDispatcherRule] installs `Dispatchers.Main` so [viewModelScope] launches can
 * run under `runTest`.
 *
 * Coverage includes repository writes, dialog state changes, crop writes,
 * repository-driven UI state updates, and crop writer failure handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppearanceSettings.Default)
        override val appearance: StateFlow<AppearanceSettings> = state.asStateFlow()
        override suspend fun setColorMode(colorMode: ColorMode) {
            state.value = state.value.copy(colorMode = colorMode)
        }
        override suspend fun setKeyColor(keyColorArgb: Int) {
            state.value = state.value.copy(keyColorArgb = keyColorArgb)
        }
        override suspend fun setPaletteStyleName(name: String) {
            state.value = state.value.copy(paletteStyleName = name)
        }
        override suspend fun setColorSpecName(name: String) {
            state.value = state.value.copy(colorSpecName = name)
        }
    }

    private fun viewModel(
        repo: SettingsRepository = FakeSettingsRepository(),
        reader: () -> CropValues = { CropValues(192, 121, 960, 605) },
        writer: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    ): SettingsViewModel = SettingsViewModel(
        settingsRepository = repo,
        cropConfigReader = reader,
        cropConfigWriter = writer,
    )

    @Test
    fun `ColorModeSelected propagates to repository`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo = repo)
        vm.handleEvent(SettingsEvent.ColorModeSelected(ColorMode.DARK_AMOLED))
        advanceUntilIdle()
        assertEquals(ColorMode.DARK_AMOLED, repo.appearance.first().colorMode)
    }

    @Test
    fun `KeyColorSelected propagates to repository`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo = repo)
        vm.handleEvent(SettingsEvent.KeyColorSelected(0xFFC9A227.toInt()))
        advanceUntilIdle()
        assertEquals(0xFFC9A227.toInt(), repo.appearance.first().keyColorArgb)
    }

    @Test
    fun `PaletteStyleSelected and ColorSpecSelected propagate`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo = repo)
        vm.handleEvent(SettingsEvent.PaletteStyleSelected("Vibrant"))
        vm.handleEvent(SettingsEvent.ColorSpecSelected("SPEC_2021"))
        advanceUntilIdle()
        val s = repo.appearance.first()
        assertEquals("Vibrant", s.paletteStyleName)
        assertEquals("SPEC_2021", s.colorSpecName)
    }

    @Test
    fun `ToggleAdvanced flips uiState advancedExpanded`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.advancedExpanded)
        vm.handleEvent(SettingsEvent.ToggleAdvanced)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.advancedExpanded)
        vm.handleEvent(SettingsEvent.ToggleAdvanced)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.advancedExpanded)
    }

    @Test
    fun `Open and Close SeedColorPicker mutate uiState`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.seedColorPickerOpen)
        vm.handleEvent(SettingsEvent.OpenSeedColorPicker)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.seedColorPickerOpen)
        vm.handleEvent(SettingsEvent.CloseSeedColorPicker)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.seedColorPickerOpen)
    }

    @Test
    fun `SaveCropAspect writes to writer and updates uiState`() = runTest {
        var written: CropValues? = null
        val vm = viewModel(
            reader = { written ?: CropValues(192, 121, 960, 605) },
            writer = { x, y, w, h -> written = CropValues(x, y, w, h) },
        )
        vm.handleEvent(SettingsEvent.OpenCropAspectDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.cropAspectDialogOpen)

        vm.handleEvent(SettingsEvent.SaveCropAspect(4, 3))
        advanceUntilIdle()
        assertEquals(CropValues(4, 3, 960, 605), written)
        assertEquals(4, vm.uiState.value.cropAspectX)
        assertEquals(3, vm.uiState.value.cropAspectY)
        assertFalse(vm.uiState.value.cropAspectDialogOpen)
    }

    @Test
    fun `SaveCropSize writes width and height and updates uiState`() = runTest {
        var written: CropValues? = null
        val vm = viewModel(
            reader = { written ?: CropValues(192, 121, 960, 605) },
            writer = { x, y, w, h -> written = CropValues(x, y, w, h) },
        )
        vm.handleEvent(SettingsEvent.OpenCropSizeDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.cropSizeDialogOpen)

        vm.handleEvent(SettingsEvent.SaveCropSize(1920, 1080))
        advanceUntilIdle()
        assertEquals(CropValues(192, 121, 1920, 1080), written)
        assertEquals(1920, vm.uiState.value.cropMaxWidth)
        assertEquals(1080, vm.uiState.value.cropMaxHeight)
        assertFalse(vm.uiState.value.cropSizeDialogOpen)
    }

    @Test
    fun `uiState emits repository appearance changes`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo = repo)
        advanceUntilIdle()
        assertEquals(ColorMode.SYSTEM, vm.uiState.value.appearance.colorMode)

        vm.handleEvent(SettingsEvent.ColorModeSelected(ColorMode.MONET_DARK))
        advanceUntilIdle()
        assertEquals(ColorMode.MONET_DARK, vm.uiState.value.appearance.colorMode)
    }

    /**
     * When `cropConfigWriter` throws, [R.string.error_save_crop_config_failed] is
     * emitted, the dialog remains open, and local crop values are unchanged.
     */
    @Test
    fun `SaveCropAspect handles writer exception emits errorEvents and keeps dialog open`() = runTest {
        val vm = viewModel(
            reader = { CropValues(192, 121, 960, 605) },
            writer = { _, _, _, _ -> throw RuntimeException("simulated write failure") },
        )
        // Open the dialog before attempting to save.
        vm.handleEvent(SettingsEvent.OpenCropAspectDialog)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.cropAspectDialogOpen)

        val collected = mutableListOf<UiText>()
        val job = launch { vm.errorEvents.toList(collected) }
        advanceUntilIdle()
        vm.handleEvent(SettingsEvent.SaveCropAspect(4, 3))
        advanceUntilIdle()
        job.cancel()

        assertEquals("应 emit 一个 error 事件", 1, collected.size)
        assertEquals(R.string.error_save_crop_config_failed, collected[0].resId)
        // Crop values remain unchanged and the dialog stays open for retry.
        assertEquals(192, vm.uiState.value.cropAspectX)
        assertEquals(121, vm.uiState.value.cropAspectY)
        assertTrue("失败应保持对话框打开供用户重试", vm.uiState.value.cropAspectDialogOpen)
    }
}
