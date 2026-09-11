package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import voidmei.telemetry.*

class HudMapBackgroundGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hudLoadsBackgroundRetriesFailuresAndClearsOldMapOnGenerationChange() {
        val fixture = Json.parseToJsonElement(Files.readString(Path.of("../script/mock_scenarios/snapshots/map_wide.json"))).jsonObject
        val info = AtomicReference(fixture.getValue("/map_info.json").jsonObject)
        val broken = AtomicBoolean(true)
        val images = AtomicInteger()
        val bytes = Base64.getDecoder().decode(fixture.getValue("/map.img").jsonObject.getValue("base64").jsonPrimitive.content)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/map_info.json") { exchange ->
            val data = info.get().toString().encodeToByteArray()
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.createContext("/map.img") { exchange ->
            images.incrementAndGet()
            val data = if (broken.get()) "invalid image".encodeToByteArray() else bytes
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.start()
        val snapshot = MapSnapshot(MapTelemetryParser.info(info.get().toString())!!, emptyList())
        val shared = MutableStateFlow<MapConnection>(MapConnection.Available(snapshot))
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
                HudMapObjects("http://127.0.0.1:${server.address.port}", shared, "战场地图")
            } } }
            fun waitForFailure() = compose.waitUntil(5000) {
                compose.onAllNodes(hasText("底图不可用", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            fun waitForImage() = compose.waitUntil(8000) {
                compose.onAllNodesWithContentDescription("地图底图与对象位置方向").fetchSemanticsNodes().isNotEmpty()
            }
            waitForFailure()
            broken.set(false)
            waitForImage()
            assertEquals(2, images.get())
            broken.set(true)
            val next = JsonObject(info.get() + ("map_generation" to JsonPrimitive(snapshot.bounds.generation!! + 1)))
            info.set(next)
            compose.runOnIdle { shared.value = MapConnection.Available(snapshot.copy(bounds = MapTelemetryParser.info(next.toString())!!)) }
            waitForFailure()
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
            compose.onNodeWithContentDescription("地图对象位置与方向示意，不含底图").assertIsDisplayed()
            broken.set(false)
            waitForImage()
            compose.runOnIdle { shared.value = MapConnection.Waiting }
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
        } finally { server.stop(0) }
    }
}
