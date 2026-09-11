package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
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
import voidmei.config.*

class HudMapBackgroundGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reconnectAndEndpointChangeDiscardCachedImageEvenWithIdenticalMapMetadata() {
        val info = """{"valid":true,"map_min":[0,0],"map_max":[1000,1000],"map_generation":1}"""
        val snapshot = MapSnapshot(MapTelemetryParser.info(info)!!, emptyList())
        fun png(rgb: Int): ByteArray {
            val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (y in 0..1) for (x in 0..1) image.setRGB(x, y, rgb)
            return java.io.ByteArrayOutputStream().use { out -> javax.imageio.ImageIO.write(image, "png", out); out.toByteArray() }
        }
        val image = AtomicReference(png(0xFF0000))
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/map_info.json") { exchange ->
            val data = info.encodeToByteArray()
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.createContext("/map.img") { exchange ->
            requests.incrementAndGet()
            val data = image.get()
            exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
        }
        server.start()
        var connection by mutableStateOf<ConnectionState>(hudPreviewFlight())
        var endpoint by mutableStateOf("http://127.0.0.1:${server.address.port}")
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
                val shared = rememberTelemetryMapSession(endpoint, connection, 0) {
                    kotlinx.coroutines.flow.flowOf(MapConnection.Available(snapshot))
                }
                HudPanel(connection, AppSettings(hudSceneLayout = HudSceneLayout(500, 500,
                    listOf(HudRegion("map", HudRegionContent.MAP, 0, 0, 500, 500)))),
                    emptyList(), null, mapEndpoint = endpoint, sharedMap = shared) {}
            } } }
            fun waitForImage() = compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription("地图底图与对象位置方向").fetchSemanticsNodes().isNotEmpty()
            }
            fun isRed(): Boolean {
                val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
                val pixel = pixels[pixels.width / 4, pixels.height / 4]
                return pixel.red > pixel.blue
            }
            waitForImage()
            kotlin.test.assertTrue(isRed())
            assertEquals(1, requests.get())
            compose.runOnIdle { connection = ConnectionState.Delayed }
            compose.runOnIdle { connection = hudPreviewFlight() }
            waitForImage()
            assertEquals(1, requests.get())
            compose.runOnIdle { connection = ConnectionState.Disconnected("test") }
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
            image.set(png(0x0000FF))
            compose.runOnIdle { connection = hudPreviewFlight() }
            waitForImage()
            kotlin.test.assertFalse(isRed())
            assertEquals(2, requests.get())
            image.set(png(0xFF0000))
            compose.runOnIdle { endpoint = "http://localhost:${server.address.port}" }
            waitForImage()
            kotlin.test.assertTrue(isRed())
            assertEquals(3, requests.get())
        } finally { server.stop(0) }
    }

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
        var connection by mutableStateOf<ConnectionState>(hudPreviewFlight())
        var second by mutableStateOf(false)
        try {
            compose.setContent { MaterialTheme { Box(Modifier.size(1000.dp, 500.dp)) {
                val region = HudRegion("map", HudRegionContent.MAP, 0, 0, 500, 500)
                HudPanel(connection, AppSettings(hudSceneLayout = HudSceneLayout(1000, 500,
                    if (second) listOf(region, region.copy(id = "second", x = 500)) else listOf(region))),
                    emptyList(), null, mapEndpoint = "http://127.0.0.1:${server.address.port}", sharedMap = shared) {}
            } } }
            fun waitForFailure() = compose.waitUntil(5000) {
                compose.onAllNodes(hasText("底图不可用", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            fun waitForImage() = compose.waitUntil(8000) {
                compose.onAllNodesWithContentDescription("地图底图与对象位置方向").fetchSemanticsNodes().size == (if (second) 2 else 1)
            }
            waitForFailure()
            broken.set(false)
            waitForImage()
            assertEquals(2, images.get())
            compose.runOnIdle { second = true }
            waitForImage()
            assertEquals(2, images.get())
            compose.runOnIdle { connection = ConnectionState.Delayed }
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
            compose.runOnIdle { connection = hudPreviewFlight() }
            waitForImage()
            assertEquals(2, images.get())
            broken.set(true)
            val next = JsonObject(info.get() + ("map_generation" to JsonPrimitive(snapshot.bounds.generation!! + 1)))
            info.set(next)
            compose.runOnIdle { shared.value = MapConnection.Available(snapshot.copy(bounds = MapTelemetryParser.info(next.toString())!!)) }
            waitForFailure()
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
            compose.onAllNodesWithContentDescription("地图对象位置与方向示意，不含底图").assertCountEquals(2)
            broken.set(false)
            waitForImage()
            compose.runOnIdle { shared.value = MapConnection.Waiting }
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
        } finally { server.stop(0) }
    }
}
