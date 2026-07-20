package com.luoshui.paycardeditor.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.luoshui.paycardeditor.app.theme.PayCardTheme
import com.luoshui.paycardeditor.model.CardSnapshotState
import com.luoshui.paycardeditor.model.HomeState
import com.luoshui.paycardeditor.model.ModuleStatusLevel
import com.luoshui.paycardeditor.model.ModuleStatusState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `active module is presented as one primary status`() {
        renderStatus(
            level = ModuleStatusLevel.ACTIVE,
            title = "模块已激活",
            detail = "LSPosed · 作用域已覆盖 com.miui.tsmclient",
        )

        assertOnlyStatus("已激活", "模块已激活")
    }

    @Test
    fun `waiting module is presented as one primary status`() {
        renderStatus(
            level = ModuleStatusLevel.WAITING,
            title = "等待连接",
            detail = "尚未连接到 libxposed 服务。",
        )

        assertOnlyStatus("等待中", "等待连接")
    }

    @Test
    fun `inactive module is presented as one primary status`() {
        renderStatus(
            level = ModuleStatusLevel.INACTIVE,
            title = "模块未激活",
            detail = "当前作用域未覆盖 com.miui.tsmclient。",
        )

        assertOnlyStatus("未激活", "模块未激活")
    }

    private fun renderStatus(
        level: ModuleStatusLevel,
        title: String,
        detail: String,
    ) {
        val uiState = HomeUiState(
            homeState = HomeState(
                moduleStatus = ModuleStatusState(
                    level = level,
                    title = title,
                    detail = detail,
                ),
                cardState = CardSnapshotState(),
            ),
        )

        rule.setContent {
            PayCardTheme {
                HomeScreen(uiState = uiState, onEvent = {})
            }
        }
    }

    private fun assertOnlyStatus(expected: String, redundantTitle: String) {
        rule.onNodeWithText(expected).assertIsDisplayed()
        rule.onAllNodesWithText(expected).assertCountEquals(1)
        rule.onAllNodesWithText("已连接").assertCountEquals(0)
        rule.onAllNodesWithText("未连接").assertCountEquals(0)
        rule.onAllNodesWithText(redundantTitle).assertCountEquals(0)
    }
}
