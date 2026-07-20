package com.luoshui.paycardeditor.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class TonalCardTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `non-clickable card renders content and is not clickable`() {
        rule.setContent {
            PayCardTheme {
                TonalCard(modifier = Modifier.testTag("card")) {
                    Text("hello")
                }
            }
        }
        rule.onNodeWithText("hello").assertExists()
        rule.onNodeWithTag("card").assertHasNoClickAction()
    }

    @Test
    fun `clickable card invokes onClick`() {
        var clicked = false
        rule.setContent {
            PayCardTheme {
                TonalCard(
                    modifier = Modifier.testTag("card"),
                    onClick = { clicked = true },
                ) {
                    Text("hello")
                }
            }
        }
        rule.onNodeWithTag("card").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertTrue(clicked)
    }
}
