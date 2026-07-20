package com.luoshui.paycardeditor.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI Test for [SettingsScreen].
 *
 * The tests bypass the ViewModel by constructing [SettingsUiState] directly and
 * capturing [SettingsEvent] callbacks. They verify main row rendering, row click
 * events, and the additional palette/spec rows when advanced settings are expanded.
 *
 * Robolectric `@Config(sdk = [35])` matches the other Compose UI tests. Wrapping
 * directly in [PayCardTheme] avoids the application-level wrapper and its default
 * Application cast fallback in Robolectric.
 *
 * Assertion strategy:
 *  - Use [SemanticsNodeInteraction.assertExists] instead of `assertIsDisplayed()`
 *    because Robolectric can report false negatives on a zero-sized host surface.
 *  - Trigger row clicks with [performSemanticsAction] and `SemanticsActions.OnClick`
 *    instead of `performClick()`. The latter depends on hit testing and positive
 *    layout pixels, while the semantics action directly verifies the row click
 *    contract this unit test cares about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun defaultState(
        advancedExpanded: Boolean = false,
        seedColorPickerOpen: Boolean = false,
        cropAspectDialogOpen: Boolean = false,
        cropSizeDialogOpen: Boolean = false,
    ) = SettingsUiState(
        appearance = AppearanceSettings.Default,
        cropAspectX = 192,
        cropAspectY = 121,
        cropMaxWidth = 960,
        cropMaxHeight = 605,
        advancedExpanded = advancedExpanded,
        seedColorPickerOpen = seedColorPickerOpen,
        cropAspectDialogOpen = cropAspectDialogOpen,
        cropSizeDialogOpen = cropSizeDialogOpen,
    )

    /** Triggers a node click through its semantic OnClick action. */
    private fun SemanticsNodeInteraction.invokeOnClick() {
        performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `renders appearance and crop rows`() {
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("主题模式").assertExists()
        rule.onNodeWithText("主色").assertExists()
        rule.onNodeWithText("高级设置").assertExists()
        rule.onNodeWithText("裁剪比例").assertExists()
        rule.onNodeWithText("最大输出尺寸").assertExists()
    }

    @Test
    fun `clicking key color row fires OpenSeedColorPicker`() {
        val events = mutableListOf<SettingsEvent>()
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("主色").invokeOnClick()
        rule.waitForIdle()
        assertTrue(
            "expected OpenSeedColorPicker in $events",
            events.contains(SettingsEvent.OpenSeedColorPicker),
        )
    }

    @Test
    fun `clicking advanced settings fires ToggleAdvanced`() {
        val events = mutableListOf<SettingsEvent>()
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("高级设置").invokeOnClick()
        rule.waitForIdle()
        assertTrue(
            "expected ToggleAdvanced in $events",
            events.contains(SettingsEvent.ToggleAdvanced),
        )
    }

    @Test
    fun `clicking advanced settings expands advanced rows`() {
        var state by mutableStateOf(defaultState())
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = state,
                    onEvent = { event ->
                        if (event == SettingsEvent.ToggleAdvanced) {
                            state = state.copy(advancedExpanded = !state.advancedExpanded)
                        }
                    },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("高级设置").invokeOnClick()
        rule.waitForIdle()
        rule.onNodeWithText("调色板风格").assertExists()
        rule.onNodeWithText("颜色规范").assertExists()
        rule.onNodeWithText("收起高级").assertExists()
    }

    @Test
    fun `advanced expanded shows palette and spec rows`() {
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(advancedExpanded = true),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("调色板风格").assertExists()
        rule.onNodeWithText("颜色规范").assertExists()
        rule.onNodeWithText("收起高级").assertExists()
    }

    @Test
    fun `clicking crop aspect row fires OpenCropAspectDialog`() {
        val events = mutableListOf<SettingsEvent>()
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("裁剪比例").invokeOnClick()
        rule.waitForIdle()
        assertTrue(
            "expected OpenCropAspectDialog in $events",
            events.contains(SettingsEvent.OpenCropAspectDialog),
        )
    }

    @Test
    fun `clicking maximum output size row fires OpenCropSizeDialog`() {
        val events = mutableListOf<SettingsEvent>()
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("最大输出尺寸").invokeOnClick()
        rule.waitForIdle()
        assertTrue(
            "expected OpenCropSizeDialog in $events",
            events.contains(SettingsEvent.OpenCropSizeDialog),
        )
    }

    @Test
    fun `key color summary shows system default when argb is zero`() {
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        // This label appears in both the color mode summary and key color summary.
        rule.onAllNodesWithText("跟随系统").assertCountEquals(2)
    }

    @Test
    fun `crop summary reflects uiState values`() {
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState().copy(
                        cropAspectX = 16,
                        cropAspectY = 9,
                        cropMaxWidth = 1920,
                        cropMaxHeight = 1080,
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("16 : 9").assertExists()
        rule.onNodeWithText("1920 × 1080").assertExists()
    }

    @Test
    fun `crop aspect dialog appears when state flag is true`() {
        rule.setContent {
            PayCardTheme {
                SettingsScreen(
                    uiState = defaultState(cropAspectDialogOpen = true),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        // The save button and ratio field labels prove the aspect dialog is rendered.
        rule.onNodeWithText("保存").assertExists()
        rule.onNodeWithText("比例 X").assertExists()
        rule.onNodeWithText("比例 Y").assertExists()
    }

    @Test
    fun `ColorMode SYSTEM is the default selected mode`() {
        // Pure logic check for the default selection and exhaustive ColorMode coverage.
        assertEquals(ColorMode.SYSTEM, AppearanceSettings.Default.colorMode)
        assertEquals(7, ColorMode.entries.size)
    }
}
