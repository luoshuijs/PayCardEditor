package com.luoshui.paycardeditor.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsAtomsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `SettingsSection renders title and child row`() {
        rule.setContent {
            PayCardTheme {
                SettingsSection(title = "外观") {
                    SettingsNavigationRow(title = "主题模式", onClick = {})
                }
            }
        }
        rule.onNodeWithText("外观").assertIsDisplayed()
        rule.onNodeWithText("主题模式").assertIsDisplayed()
    }

    @Test
    fun `SettingsSwitchRow tap toggles via row click`() {
        var newValue: Boolean? = null
        rule.setContent {
            PayCardTheme {
                SettingsSection("S") {
                    SettingsSwitchRow(
                        title = "Foo",
                        checked = false,
                        onCheckedChange = { newValue = it },
                        modifier = Modifier.testTag("row"),
                    )
                }
            }
        }
        rule.onNodeWithTag("row").performClick()
        rule.waitForIdle()
        assertEquals(true, newValue)
    }

    @Test
    fun `SettingsRadioRow tap invokes onSelect with the row value`() {
        var picked: String? = null
        rule.setContent {
            PayCardTheme {
                SettingsSection("S") {
                    SettingsRadioRow(
                        title = "A",
                        value = "a",
                        selected = "b",
                        onSelect = { picked = it },
                        modifier = Modifier.testTag("a"),
                    )
                    SettingsRadioRow(
                        title = "B",
                        value = "b",
                        selected = "b",
                        onSelect = { picked = it },
                        modifier = Modifier.testTag("b"),
                    )
                }
            }
        }
        rule.onNodeWithTag("a").performClick()
        rule.waitForIdle()
        assertEquals("a", picked)
    }

    @Test
    fun `SettingsNavigationRow onClick fires`() {
        var clicked = false
        rule.setContent {
            PayCardTheme {
                SettingsSection("S") {
                    SettingsNavigationRow(
                        title = "Nav",
                        onClick = { clicked = true },
                        modifier = Modifier.testTag("nav"),
                    )
                }
            }
        }
        rule.onNodeWithTag("nav").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertTrue(clicked)
    }

    @Test
    fun `SettingsDropdownRow row default summary shows current option label`() {
        rule.setContent {
            PayCardTheme {
                SettingsSection("S") {
                    SettingsDropdownRow(
                        title = "Palette",
                        options = listOf("TonalSpot", "Vibrant", "Expressive"),
                        selected = "Vibrant",
                        optionLabel = { it },
                        onSelect = {},
                        modifier = Modifier.testTag("dd"),
                    )
                }
            }
        }
        rule.onNodeWithText("Palette").assertIsDisplayed()
        rule.onNodeWithText("Vibrant").assertIsDisplayed()
    }
}
