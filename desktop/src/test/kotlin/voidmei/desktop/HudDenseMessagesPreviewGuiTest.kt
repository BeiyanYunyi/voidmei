package voidmei.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.config.*

class HudDenseMessagesPreviewGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun denseMessagesRespectIndependentLimitsAndMissingModeClearsThem() {
        val settings = AppSettings(hudEnabled = false, hudSceneLayout = HudSceneLayout(800, 300, listOf(
            HudRegion("events", HudRegionContent.MESSAGES, 0, 0, 400, 180, fields = listOf("event"), messageLimit = 10),
            HudRegion("damage", HudRegionContent.MESSAGES, 400, 0, 400, 180, fields = listOf("damage"), messageLimit = 20),
        )))
        compose.setContent { HudLayoutPreviewWindow(settings, 0) {} }
        compose.onNodeWithTag("hud-preview-dense-messages").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("事件 #20 · 示例事件消息 20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("事件 #20 · 示例事件消息 20").assertIsDisplayed()
        compose.onNodeWithText("事件 #11 · 示例事件消息 11").assertExists()
        compose.onNodeWithText("事件 #10 · 示例事件消息 10").assertDoesNotExist()
        compose.onNodeWithText("损伤 #1 · 示例损伤消息 1").assertExists()
        compose.onAllNodesWithTag("hud-scroll-indicator").assertCountEquals(2)
        compose.onNodeWithText("缺失数据").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("尚无所选类别的消息").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithTag("hud-preview-dense-messages").assertIsNotEnabled()
        compose.onNodeWithText("事件 #20 · 示例事件消息 20").assertDoesNotExist()
        compose.onAllNodesWithText("尚无所选类别的消息").assertCountEquals(2)
        compose.onNodeWithText("正常读数").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("事件 #20 · 示例事件消息 20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("事件 #20 · 示例事件消息 20").assertIsDisplayed()
        compose.onNodeWithTag("hud-preview-dense-messages").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("事件 #1 · 示例事件消息").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("事件 #1 · 示例事件消息").assertIsDisplayed()
        compose.onAllNodesWithTag("hud-scroll-indicator").assertCountEquals(0)
    }
}
