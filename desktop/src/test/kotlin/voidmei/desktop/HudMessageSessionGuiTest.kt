package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*
import kotlin.test.*

class HudMessageSessionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pauseResumesFromSavedHistoryButReloadAndDisconnectStartFresh() {
        var enabled by mutableStateOf(true)
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        var session: HudMessageSession? = null
        val seeds = mutableListOf<HudMessageState>()
        var active = 0
        val saved = HudMessageState(listOf(HudMessage(HudMessageKind.DAMAGE, 50, "已有损伤")),
            lastEventId = 30, lastDamageId = 50)
        compose.setContent {
            val messages = rememberHudMessageSession("http://test", connection, enabled, 0) { _, initial ->
                flow {
                    seeds += initial
                    active++
                    try { emit(saved); awaitCancellation() } finally { active-- }
                }
            }
            SideEffect { session = messages }
        }
        compose.waitUntil(5000) { session?.state == saved && active == 1 }
        compose.runOnIdle { enabled = false }
        compose.waitUntil(5000) { active == 0 }
        compose.runOnIdle { assertEquals(saved, session!!.state); enabled = true }
        compose.waitUntil(5000) { seeds.size == 2 && active == 1 }
        compose.runOnIdle {
            assertEquals(saved, seeds[1])
            session!!.reload()
        }
        compose.waitUntil(5000) { seeds.size == 3 && active == 1 }
        compose.runOnIdle {
            assertEquals(HudMessageState(emptyList()), seeds[2])
            connection = ConnectionState.Disconnected("test")
        }
        compose.waitUntil(5000) { active == 0 && session?.state?.messages?.isEmpty() == true }
        compose.runOnIdle { connection = hudPreviewFlight() }
        compose.waitUntil(5000) { seeds.size == 4 && active == 1 }
        compose.runOnIdle { assertEquals(HudMessageState(emptyList()), seeds[3]) }
    }

    @Test fun consumersShareReaderAndDelaysKeepHistoryUntilRealDisconnect() {
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        var enabled by mutableStateOf(false)
        var panelExpanded by mutableStateOf(false)
        var session: HudMessageSession? = null
        var starts = 0
        var active = 0
        val source: (String, HudMessageState) -> kotlinx.coroutines.flow.Flow<HudMessageState> = { _, _ ->
            flow {
                starts++; active++
                try {
                    emit(HudMessageState(listOf(HudMessage(HudMessageKind.DAMAGE, starts, "损伤示例 $starts"))))
                    awaitCancellation()
                } finally { active-- }
            }
        }
        compose.setContent {
            val messages = rememberHudMessageSession("http://test", connection, enabled || panelExpanded, 0, source)
            SideEffect { session = messages }
            MaterialTheme { Column {
                HudRecentMessages(messages.state); HudRecentMessages(messages.state)
                HudMessagesPanel("http://test", connection as? ConnectionState.Flying, messages) { panelExpanded = it }
            } }
        }
        compose.runOnIdle { assertEquals(0, starts); enabled = true }
        compose.waitUntil(5000) { session?.state?.messages?.size == 1 }
        compose.onAllNodesWithText("损伤 #1 · 损伤示例 1").assertCountEquals(2)
        compose.onNodeWithText("查看游戏消息").performClick()
        compose.onAllNodesWithText("损伤 #1 · 损伤示例 1").assertCountEquals(3)
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, active) }
        compose.onNodeWithText("收起游戏消息").performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, active); connection = ConnectionState.Delayed }
        compose.onAllNodesWithText("损伤 #1 · 损伤示例 1").assertCountEquals(2)
        compose.runOnIdle { assertEquals(1, starts); connection = hudPreviewFlight() }
        compose.runOnIdle { assertEquals(1, starts); session!!.reload() }
        compose.waitUntil(5000) { starts == 2 && session?.state?.messages?.firstOrNull()?.id == 2 }
        compose.runOnIdle { assertEquals(1, active); connection = ConnectionState.Disconnected("test") }
        compose.waitUntil(5000) { active == 0 && session?.state?.messages?.isEmpty() == true }
        compose.runOnIdle { enabled = false }
    }

    @Test fun regionShowsFiveRecentMessagesAndRetainsThemOnRequestFailure() {
        val scene = HudSceneLayout(500, 400, listOf(HudRegion("messages", HudRegionContent.MESSAGES, 0, 0, 500, 400)))
        val settings = AppSettings(hudSceneLayout = scene)
        var messages by mutableStateOf(HudMessageState((1..7).map { HudMessage(HudMessageKind.EVENT, it, "消息 $it") }))
        var connection: ConnectionState by mutableStateOf(hudPreviewFlight())
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 400.dp)) {
            HudPanel(connection, settings, emptyList(), null, messages = messages) {}
        } } }
        compose.onNodeWithText("事件 #7 · 消息 7").assertIsDisplayed()
        compose.onNodeWithText("事件 #3 · 消息 3").assertIsDisplayed()
        compose.onNodeWithText("事件 #2 · 消息 2").assertDoesNotExist()
        compose.runOnIdle { messages = messages.copy(error = "timeout") }
        compose.onNodeWithText("消息更新失败（保留已有记录）").assertIsDisplayed()
        compose.onNodeWithText("事件 #7 · 消息 7").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.Delayed }
        compose.onNodeWithText("事件 #7 · 消息 7").assertDoesNotExist()
        compose.onNodeWithText("遥测更新延迟 · 等待当前请求").assertIsDisplayed()
    }
}
