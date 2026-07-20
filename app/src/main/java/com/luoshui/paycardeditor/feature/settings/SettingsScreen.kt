package com.luoshui.paycardeditor.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.app.theme.BrandSeedGold
import com.luoshui.paycardeditor.app.theme.ColorMode
import com.luoshui.paycardeditor.app.theme.colorSpecOptions
import com.luoshui.paycardeditor.app.theme.paletteStyleOptions
import com.luoshui.paycardeditor.ui.EmptyErrorEvents
import com.luoshui.paycardeditor.ui.UiText
import com.luoshui.paycardeditor.ui.components.SettingsDropdownRow
import com.luoshui.paycardeditor.ui.components.SettingsNavigationRow
import com.luoshui.paycardeditor.ui.components.SettingsSection
import com.luoshui.paycardeditor.ui.components.UiErrorEffect
import kotlinx.coroutines.flow.SharedFlow

/**
 * Settings screen.
 *
 * The screen owns no ViewModel reference; all user actions go through [onEvent]
 * for Compose UI tests. `Column + verticalScroll` is used because the row count
 * is small and stable, and eager composition lets Robolectric tests query crop
 * rows in zero-size hosts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    errorEvents: SharedFlow<UiText> = EmptyErrorEvents,
) {
    UiErrorEffect(errorEvents)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Eager composition keeps the fixed settings rows visible to zero-size Robolectric hosts.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            AppearanceSection(uiState, onEvent)
            CropSection(uiState, onEvent)
            Spacer(Modifier.height(32.dp))
        }

        if (uiState.seedColorPickerOpen) {
            SeedColorPickerSheet(
                selectedArgb = uiState.appearance.keyColorArgb,
                onSelect = { argb ->
                    onEvent(SettingsEvent.KeyColorSelected(argb))
                    onEvent(SettingsEvent.CloseSeedColorPicker)
                },
                onDismiss = { onEvent(SettingsEvent.CloseSeedColorPicker) },
            )
        }
        if (uiState.cropAspectDialogOpen) {
            CropAspectEditorDialog(
                initialX = uiState.cropAspectX,
                initialY = uiState.cropAspectY,
                onSave = { x, y -> onEvent(SettingsEvent.SaveCropAspect(x, y)) },
                onDismiss = { onEvent(SettingsEvent.CloseCropAspectDialog) },
            )
        }
        if (uiState.cropSizeDialogOpen) {
            CropSizeEditorDialog(
                initialWidth = uiState.cropMaxWidth,
                initialHeight = uiState.cropMaxHeight,
                onSave = { w, h -> onEvent(SettingsEvent.SaveCropSize(w, h)) },
                onDismiss = { onEvent(SettingsEvent.CloseCropSizeDialog) },
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
        // Resolve labels before optionLabel because that lambda is not composable.
        val colorModeLabels: Map<ColorMode, String> = ColorMode.entries.associateWith {
            stringResource(colorModeLabel(it))
        }
        SettingsDropdownRow(
            title = stringResource(R.string.settings_row_color_mode),
            options = ColorMode.entries,
            selected = uiState.appearance.colorMode,
            optionLabel = { colorModeLabels.getValue(it) },
            onSelect = { onEvent(SettingsEvent.ColorModeSelected(it)) },
        )

        val keyColorSummary = if (uiState.appearance.keyColorArgb == 0) {
            stringResource(R.string.seed_color_follow_system)
        } else {
            "#%08X".format(uiState.appearance.keyColorArgb)
        }
        SettingsNavigationRow(
            title = stringResource(R.string.settings_row_key_color),
            summary = keyColorSummary,
            onClick = { onEvent(SettingsEvent.OpenSeedColorPicker) },
            trailing = {
                val swatchColor = if (uiState.appearance.keyColorArgb == 0) {
                    BrandSeedGold
                } else {
                    Color(uiState.appearance.keyColorArgb)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(swatchColor),
                )
            },
        )

        SettingsNavigationRow(
            title = stringResource(R.string.settings_row_advanced),
            summary = if (uiState.advancedExpanded) {
                stringResource(R.string.advanced_collapse)
            } else {
                stringResource(R.string.advanced_expand)
            },
            onClick = { onEvent(SettingsEvent.ToggleAdvanced) },
        )

        if (uiState.advancedExpanded) {
            SettingsDropdownRow(
                title = stringResource(R.string.settings_row_palette_style),
                options = paletteStyleOptions,
                selected = uiState.appearance.paletteStyleName,
                optionLabel = { it },
                onSelect = { onEvent(SettingsEvent.PaletteStyleSelected(it)) },
            )
            SettingsDropdownRow(
                title = stringResource(R.string.settings_row_color_spec),
                options = colorSpecOptions,
                selected = uiState.appearance.colorSpecName,
                optionLabel = { it },
                onSelect = { onEvent(SettingsEvent.ColorSpecSelected(it)) },
            )
        }
    }
}

@Composable
private fun CropSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_eyebrow)) {
        SettingsNavigationRow(
            title = stringResource(R.string.settings_row_crop_aspect_title),
            summary = stringResource(
                R.string.settings_value_aspect_format,
                uiState.cropAspectX,
                uiState.cropAspectY,
            ),
            onClick = { onEvent(SettingsEvent.OpenCropAspectDialog) },
        )
        SettingsNavigationRow(
            title = stringResource(R.string.settings_row_crop_size_title),
            summary = stringResource(
                R.string.settings_value_size_format,
                uiState.cropMaxWidth,
                uiState.cropMaxHeight,
            ),
            onClick = { onEvent(SettingsEvent.OpenCropSizeDialog) },
        )
    }
}

/** Maps [ColorMode] to its string resource id. */
private fun colorModeLabel(mode: ColorMode): Int = when (mode) {
    ColorMode.SYSTEM       -> R.string.color_mode_system
    ColorMode.LIGHT        -> R.string.color_mode_light
    ColorMode.DARK         -> R.string.color_mode_dark
    ColorMode.MONET_SYSTEM -> R.string.color_mode_monet_system
    ColorMode.MONET_LIGHT  -> R.string.color_mode_monet_light
    ColorMode.MONET_DARK   -> R.string.color_mode_monet_dark
    ColorMode.DARK_AMOLED  -> R.string.color_mode_dark_amoled
}
