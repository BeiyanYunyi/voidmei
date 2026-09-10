package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpServer
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.*

class HudMapGridGuiTest {
    @get:Rule val compose = createComposeRule()

    private class MapServer(private val rowOrigin: Int) : AutoCloseable {
        val requests = AtomicInteger()
        val fail = AtomicBoolean()
        val pauseNext = java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch?>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                pauseNext.getAndSet(null)?.await(1800, java.util.concurrent.TimeUnit.MILLISECONDS)
                val body = when (exchange.requestURI.path) {
                    "/map_info.json" -> """{"valid":true,"map_min":[-1000,-1000],"map_max":[1000,1000],"grid_steps":[200,200],"grid_zero":[-1000,$rowOrigin]}"""
                    "/map_obj.json" -> """[{"icon":"Player","x":0.35,"y":0.25}]"""
                    else -> error("Unexpected HUD request: ${exchange.requestURI}")
                }.encodeToByteArray()
                exchange.sendResponseHeaders(if (fail.get()) 503 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val endpoint get() = "http://127.0.0.1:${server.address.port}"
        override fun close() = server.stop(0)
    }

    @Test fun requestsFollowHudFlightFieldAndEndpointLifecycle() {
        MapServer(1000).use { first -> MapServer(1200).use { second ->
            val flying = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""",
                """{"valid":true,"type":"test","compass":90}""")!!, FlightMetrics())
            var connection by mutableStateOf<ConnectionState>(ConnectionState.WaitingForFlight)
            var settings by mutableStateOf(AppSettings(hudFields = listOf("heading"), hudAttitude = false, hudMechanization = false))
            var endpoint by mutableStateOf(first.endpoint)
            var showHud by mutableStateOf(true)
            compose.setContent { MaterialTheme { Box(Modifier.requiredSize(400.dp, 600.dp)) {
                val shared = key(endpoint, connection is ConnectionState.Flying) {
                    val owner = rememberCoroutineScope()
                    remember { mapStates(endpoint).shareMap(owner) }
                }
                if (showHud) HudPanel(connection, settings, emptyList(), null, mapEndpoint = endpoint, sharedMap = shared) {}
            } } }
            fun waitFor(text: String) = compose.waitUntil(5000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            fun assertStopped(server: MapServer) {
                compose.waitForIdle()
                val count = server.requests.get()
                // More than a production poll interval: composition idleness alone cannot prove cancellation.
                Thread.sleep(1250)
                assertEquals(count, server.requests.get(), "Removed HUD consumer must stop polling")
            }
            assertStopped(first)
            assertEquals(0, first.requests.get())
            compose.runOnIdle { connection = flying }
            waitFor("地图格号：C4")
            assertTrue(first.requests.get() >= 3)

            compose.runOnIdle { endpoint = second.endpoint }
            waitFor("地图格号：D4")
            compose.onNodeWithText("地图格号：C4").assertDoesNotExist()
            assertStopped(first)
            val responseGate = java.util.concurrent.CountDownLatch(1)
            second.pauseNext.set(responseGate)
            try {
                waitFor("地图格号：—")
                compose.onNodeWithText("地图格号：D4").assertDoesNotExist()
            } finally { responseGate.countDown() }
            waitFor("地图格号：D4")
            second.fail.set(true)
            waitFor("地图格号：—")
            compose.onNodeWithText("地图格号：D4").assertDoesNotExist()
            second.fail.set(false)
            waitFor("地图格号：D4")

            compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
            compose.onNodeWithText("地图格号：D4").assertDoesNotExist()
            assertStopped(second)
            compose.runOnIdle { settings = settings.copy(hudFields = listOf("heading")) }
            waitFor("地图格号：D4")
            compose.runOnIdle { connection = ConnectionState.WaitingForFlight }
            compose.onNodeWithText("地图格号：D4").assertDoesNotExist()
            assertStopped(second)
            compose.runOnIdle { connection = flying }
            waitFor("地图格号：D4")
            compose.runOnIdle { showHud = false }
            assertStopped(second)
        } }
    }
}
