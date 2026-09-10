package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.telemetry.*

class HudMessagesGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyIsLazyAndKeepsReadingPositionWhenNewMessagesArrive() {
        var state by mutableStateOf(HudMessageState((1..200).map {
            HudMessage(HudMessageKind.EVENT, it, "消息 $it")
        }))
        compose.setContent { MaterialTheme { Column { HudMessageList(state) } } }
        compose.onNodeWithText("事件 #200 · 消息 200").assertIsDisplayed()
        compose.onNodeWithText("事件 #1 · 消息 1").assertDoesNotExist()
        compose.onNodeWithTag("game-message-list").performScrollToIndex(100)
        val anchor = compose.onNodeWithText("事件 #100 · 消息 100")
        anchor.assertIsDisplayed()
        val before = anchor.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            // The history cap removes old rows as a new row is prepended on screen.
            state = HudMessageState(state.messages.drop(1) + HudMessage(HudMessageKind.DAMAGE, 200, "新损伤"))
        }
        anchor.assertIsDisplayed()
        assertEquals(before, anchor.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        compose.onNodeWithText("回到最新消息").performClick()
        compose.onNodeWithText("损伤 #200 · 新损伤").assertIsDisplayed()
        compose.onNodeWithText("事件 #200 · 消息 200").assertIsDisplayed()
        compose.onNodeWithText("事件 #100 · 消息 100").assertDoesNotExist()
    }

    @Test fun realHttpCursorsRestartAfterExplicitReloadAndFlightChange() {
        val reset = AtomicBoolean(false)
        val requests = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hudmsg") { exchange ->
            val query = exchange.requestURI.rawQuery
            requests.add(query)
            val body = if (query == "lastEvt=0&lastDmg=0") {
                if (reset.get()) """{"events":[{"id":2,"msg":"新事件"}],"damage":[]}"""
                else """{"events":[{"id":7,"msg":"旧事件"}],"damage":[{"id":9,"msg":"旧损伤"}]}"""
            } else """{"events":[],"damage":[]}"""
            val bytes = body.encodeToByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val initial = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"type":"test"}""")!!, FlightMetrics())
        var flight by mutableStateOf<ConnectionState.Flying?>(initial)
        try {
            compose.setContent { MaterialTheme { Column {
                HudMessagesPanel("http://127.0.0.1:${server.address.port}", flight)
            } } }
            compose.runOnIdle { assertTrue(requests.isEmpty()) }
            compose.onNodeWithText("查看游戏消息").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("损伤 #9 · 旧损伤").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("事件 #7 · 旧事件").assertIsDisplayed()
            compose.waitUntil(5000) { "lastEvt=7&lastDmg=9" in requests }
            reset.set(true)
            compose.onNodeWithText("重新读取游戏消息").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("事件 #2 · 新事件").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("损伤 #9 · 旧损伤").assertDoesNotExist()
            compose.onNodeWithText("事件 #7 · 旧事件").assertDoesNotExist()
            compose.waitUntil(5000) { "lastEvt=2&lastDmg=0" in requests }
            compose.runOnIdle { flight = null }
            compose.onNodeWithText("进入飞行后读取游戏消息").assertIsDisplayed()
            compose.onNodeWithText("事件 #2 · 新事件").assertDoesNotExist()
            val starts = requests.count { it == "lastEvt=0&lastDmg=0" }
            compose.runOnIdle { flight = initial }
            compose.waitUntil(5000) { requests.count { it == "lastEvt=0&lastDmg=0" } > starts }
            compose.onNodeWithText("收起游戏消息").performClick()
            compose.onNodeWithText("重新读取游戏消息").assertDoesNotExist()
        } finally { server.stop(0) }
    }
}
