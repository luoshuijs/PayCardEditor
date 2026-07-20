package com.luoshui.paycardeditor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusTagTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `each tone renders the given text`() {
        rule.setContent {
            PayCardTheme {
                Column {
                    StatusTag(text = "Neutral", tone = StatusTagTone.Neutral)
                    StatusTag(text = "Success", tone = StatusTagTone.Success)
                    StatusTag(text = "Warning", tone = StatusTagTone.Warning)
                    StatusTag(text = "Error", tone = StatusTagTone.Error)
                    StatusTag(text = "Info", tone = StatusTagTone.Info)
                }
            }
        }
        listOf("Neutral", "Success", "Warning", "Error", "Info").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }
}
