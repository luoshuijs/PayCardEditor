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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentedListTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `container with three items shows all titles`() {
        rule.setContent {
            PayCardTheme {
                SegmentedListContainer {
                    SegmentedListItem(title = "First", modifier = Modifier.testTag("a"))
                    SegmentedListItem(title = "Middle", modifier = Modifier.testTag("b"))
                    SegmentedListItem(title = "Last", modifier = Modifier.testTag("c"))
                }
            }
        }
        rule.onNodeWithText("First").assertIsDisplayed()
        rule.onNodeWithText("Middle").assertIsDisplayed()
        rule.onNodeWithText("Last").assertIsDisplayed()
    }

    @Test
    fun `item with onClick has click action and fires`() {
        var clicked = 0
        rule.setContent {
            PayCardTheme {
                SegmentedListContainer {
                    SegmentedListItem(
                        title = "Tap me",
                        onClick = { clicked++ },
                        modifier = Modifier.testTag("row"),
                    )
                }
            }
        }
        rule.onNodeWithTag("row").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(1, clicked)
    }

    @Test
    fun `item with summary renders summary text`() {
        rule.setContent {
            PayCardTheme {
                SegmentedListContainer {
                    SegmentedListItem(title = "Title", summary = "Secondary line")
                }
            }
        }
        rule.onNodeWithText("Secondary line").assertIsDisplayed()
    }
}
