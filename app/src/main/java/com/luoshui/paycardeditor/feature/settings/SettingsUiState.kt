package com.luoshui.paycardeditor.feature.settings

import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode

/**
 * Immutable UI state for [SettingsScreen].
 *
 * [appearance] comes from the DataStore-backed settings repository. Crop fields
 * come from [com.luoshui.paycardeditor.feature.studio.CropConfig] and stay in
 * shared SharedPreferences because the hook subsystem shares that state.
 * Dialog and advanced-section flags are transient screen state.
 */
data class SettingsUiState(
    val appearance: AppearanceSettings,
    val cropAspectX: Int,
    val cropAspectY: Int,
    val cropMaxWidth: Int,
    val cropMaxHeight: Int,
    val advancedExpanded: Boolean = false,
    val seedColorPickerOpen: Boolean = false,
    val cropAspectDialogOpen: Boolean = false,
    val cropSizeDialogOpen: Boolean = false,
)

/** All user events emitted by [SettingsScreen] and consumed by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ColorModeSelected(val mode: ColorMode) : SettingsEvent
    data class KeyColorSelected(val argb: Int) : SettingsEvent
    data class PaletteStyleSelected(val name: String) : SettingsEvent
    data class ColorSpecSelected(val name: String) : SettingsEvent
    data object ToggleAdvanced : SettingsEvent
    data object OpenSeedColorPicker : SettingsEvent
    data object CloseSeedColorPicker : SettingsEvent
    data object OpenCropAspectDialog : SettingsEvent
    data object CloseCropAspectDialog : SettingsEvent
    data class SaveCropAspect(val x: Int, val y: Int) : SettingsEvent
    data object OpenCropSizeDialog : SettingsEvent
    data object CloseCropSizeDialog : SettingsEvent
    data class SaveCropSize(val width: Int, val height: Int) : SettingsEvent
}
