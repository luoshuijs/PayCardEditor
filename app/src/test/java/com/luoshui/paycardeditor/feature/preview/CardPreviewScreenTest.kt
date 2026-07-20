package com.luoshui.paycardeditor.feature.preview

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardPreviewScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `entering preview requests fresh cross-process snapshot state`() {
        val events = mutableListOf<CardPreviewEvent>()
        val visible = mutableStateOf(true)

        rule.setContent {
            PayCardTheme {
                if (visible.value) {
                    CardPreviewScreen(
                        uiState = CardPreviewUiState(),
                        onEvent = events::add,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()

        assertEquals(listOf(CardPreviewEvent.Refresh, CardPreviewEvent.Refresh), events)
    }
}
