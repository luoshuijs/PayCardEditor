package com.luoshui.paycardeditor.feature.settings

import androidx.lifecycle.viewModelScope
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.data.settings.SettingsRepository
import com.luoshui.paycardeditor.ui.BaseViewModel
import com.luoshui.paycardeditor.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for [SettingsScreen].
 *
 * [settingsRepository] supplies DataStore-backed theme state. Crop settings are
 * injected through reader and writer lambdas so the ViewModel does not hold
 * Android Context and can be unit tested; those crop settings remain in shared
 * SharedPreferences. `uiState` is eagerly shared to retain transient dialog and
 * crop state across brief collector gaps.
 *
 * Appearance and crop writes are guarded so failures emit error events without
 * advancing local UI state when persistence failed.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val cropConfigReader: () -> CropValues,
    private val cropConfigWriter: (x: Int, y: Int, w: Int, h: Int) -> Unit,
) : BaseViewModel() {

    private val localState = MutableStateFlow(
        LocalUiBits(
            advancedExpanded = false,
            seedColorPickerOpen = false,
            cropAspectDialogOpen = false,
            cropSizeDialogOpen = false,
            crop = cropConfigReader(),
        )
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.appearance,
        localState,
    ) { appearance, local ->
        SettingsUiState(
            appearance = appearance,
            cropAspectX = local.crop.aspectX,
            cropAspectY = local.crop.aspectY,
            cropMaxWidth = local.crop.maxWidth,
            cropMaxHeight = local.crop.maxHeight,
            advancedExpanded = local.advancedExpanded,
            seedColorPickerOpen = local.seedColorPickerOpen,
            cropAspectDialogOpen = local.cropAspectDialogOpen,
            cropSizeDialogOpen = local.cropSizeDialogOpen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            appearance = AppearanceSettings.Default,
            cropAspectX = localState.value.crop.aspectX,
            cropAspectY = localState.value.crop.aspectY,
            cropMaxWidth = localState.value.crop.maxWidth,
            cropMaxHeight = localState.value.crop.maxHeight,
        ),
    )

    fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ColorModeSelected ->
                viewModelScope.launch {
                    try {
                        settingsRepository.setColorMode(event.mode)
                    } catch (e: Exception) {
                        emitError(UiText(R.string.error_save_appearance_failed))
                    }
                }

            is SettingsEvent.KeyColorSelected ->
                viewModelScope.launch {
                    try {
                        settingsRepository.setKeyColor(event.argb)
                    } catch (e: Exception) {
                        emitError(UiText(R.string.error_save_appearance_failed))
                    }
                }

            is SettingsEvent.PaletteStyleSelected ->
                viewModelScope.launch {
                    try {
                        settingsRepository.setPaletteStyleName(event.name)
                    } catch (e: Exception) {
                        emitError(UiText(R.string.error_save_appearance_failed))
                    }
                }

            is SettingsEvent.ColorSpecSelected ->
                viewModelScope.launch {
                    try {
                        settingsRepository.setColorSpecName(event.name)
                    } catch (e: Exception) {
                        emitError(UiText(R.string.error_save_appearance_failed))
                    }
                }

            SettingsEvent.ToggleAdvanced ->
                localState.update { it.copy(advancedExpanded = !it.advancedExpanded) }

            SettingsEvent.OpenSeedColorPicker ->
                localState.update { it.copy(seedColorPickerOpen = true) }

            SettingsEvent.CloseSeedColorPicker ->
                localState.update { it.copy(seedColorPickerOpen = false) }

            SettingsEvent.OpenCropAspectDialog ->
                localState.update { it.copy(cropAspectDialogOpen = true) }

            SettingsEvent.CloseCropAspectDialog ->
                localState.update { it.copy(cropAspectDialogOpen = false) }

            is SettingsEvent.SaveCropAspect -> {
                val cur = localState.value.crop
                if (!tryWriteCropConfig(event.x, event.y, cur.maxWidth, cur.maxHeight)) return
                localState.update {
                    it.copy(
                        crop = it.crop.copy(aspectX = event.x, aspectY = event.y),
                        cropAspectDialogOpen = false,
                    )
                }
            }

            SettingsEvent.OpenCropSizeDialog ->
                localState.update { it.copy(cropSizeDialogOpen = true) }

            SettingsEvent.CloseCropSizeDialog ->
                localState.update { it.copy(cropSizeDialogOpen = false) }

            is SettingsEvent.SaveCropSize -> {
                val cur = localState.value.crop
                if (!tryWriteCropConfig(cur.aspectX, cur.aspectY, event.width, event.height)) return
                localState.update {
                    it.copy(
                        crop = it.crop.copy(maxWidth = event.width, maxHeight = event.height),
                        cropSizeDialogOpen = false,
                    )
                }
            }
        }
    }

    /**
     * Writes crop settings synchronously and reports failures without advancing local state.
     *
     * A false return means an error event has already been emitted and the caller should stop.
     */
    private fun tryWriteCropConfig(x: Int, y: Int, w: Int, h: Int): Boolean {
        return try {
            cropConfigWriter(x, y, w, h)
            true
        } catch (e: Exception) {
            viewModelScope.launch {
                emitError(UiText(R.string.error_save_crop_config_failed))
            }
            false
        }
    }

    private data class LocalUiBits(
        val advancedExpanded: Boolean,
        val seedColorPickerOpen: Boolean,
        val cropAspectDialogOpen: Boolean,
        val cropSizeDialogOpen: Boolean,
        val crop: CropValues,
    )

    private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }
}

/**
 * Pure crop settings projection shared by production wiring and tests.
 */
data class CropValues(
    val aspectX: Int,
    val aspectY: Int,
    val maxWidth: Int,
    val maxHeight: Int,
)
