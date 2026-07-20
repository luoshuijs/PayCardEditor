package com.luoshui.paycardeditor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WarningCardTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders title and body`() {
        rule.setContent {
            PayCardTheme {
                WarningCard(
                    title = "Heads up",
                    body = "Something is off",
                    icon = Icons.Default.Warning,
                )
            }
        }
        rule.onNodeWithText("Heads up").assertIsDisplayed()
        rule.onNodeWithText("Something is off").assertIsDisplayed()
    }

    @Test
    fun `actionLabel onClick is invoked`() {
        var clicked = false
        rule.setContent {
            PayCardTheme {
                WarningCard(
                    title = "T",
                    body = "B",
                    actionLabel = "Fix it",
                    onAction = { clicked = true },
                )
            }
        }
        rule.onNodeWithText("Fix it").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertTrue(clicked)
    }
}
