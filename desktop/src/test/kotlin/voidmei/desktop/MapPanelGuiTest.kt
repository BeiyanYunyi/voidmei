package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpServer
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
import kotlin.test.*

class MapPanelGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gridPixelsFollowOffsetAndClearWithoutMetadata() {
        var bounds by mutableStateOf(voidmei.telemetry.MapBounds(
            voidmei.telemetry.MapPoint(-1000.0, -1000.0), voidmei.telemetry.MapPoint(1000.0, 1000.0), 1,
            voidmei.telemetry.MapPoint(400.0, 400.0), voidmei.telemetry.MapPoint(-900.0, 900.0)))
        compose.setContent { MaterialTheme { Column {
            MapObjectPlot(voidmei.telemetry.MapSnapshot(bounds, emptyList()))
        } } }
        fun pixel(x: Double, y: Double): androidx.compose.ui.graphics.Color {
            val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
            return pixels[(x * pixels.width).toInt(), (y * pixels.height).toInt()]
        }
        val empty = pixel(.1, .1)
        assertNotEquals(empty, pixel(.05, .1), "Offset vertical grid line must render at 5%")
        assertNotEquals(empty, pixel(.1, .05), "Offset horizontal grid line must render at 5%")
        assertEquals(empty, pixel(.5, .1), "Do not retain the old fixed quarter grid")
        compose.runOnIdle { bounds = bounds.copy(gridSteps = null) }
        compose.onNodeWithText("地图网格不可用").assertExists()
        assertEquals(pixel(.1, .1), pixel(.05, .1))
        assertEquals(pixel(.1, .1), pixel(.1, .05))
    }

    @Test fun httpMapFailureRetryGenerationChangeAndFlightLifecycle() {
        fun fixture(name: String) = Json.parseToJsonElement(Files.readString(
            Path.of("../script/mock_scenarios/snapshots/$name.json"))).jsonObject
        val current = AtomicReference(fixture("map_wide"))
        val broken = AtomicBoolean(true)
        val requests = AtomicInteger()
        val images = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        for (endpoint in listOf("/map_info.json", "/map_obj.json", "/map.img")) {
            server.createContext(endpoint) { exchange ->
                requests.incrementAndGet()
                val snapshot = current.get()
                val bytes = if (endpoint == "/map.img") {
                    images.incrementAndGet()
                    if (broken.get()) "broken image".encodeToByteArray()
                    else Base64.getDecoder().decode(snapshot.getValue(endpoint).jsonObject.getValue("base64").jsonPrimitive.content)
                } else snapshot.getValue(endpoint).toString().encodeToByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        var flying by mutableStateOf(true)
        fun waitForImage() = compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("地图底图与对象位置方向").fetchSemanticsNodes().isNotEmpty()
        }
        fun waitForRetry() = compose.waitUntil(5000) {
            compose.onAllNodesWithText("重试底图").fetchSemanticsNodes().isNotEmpty()
        }
        fun assertPixel(x: Float, y: Float, rgb: Int) {
            val bitmap = compose.onNodeWithTag("map-objects-plot").captureToImage()
            val color = bitmap.toPixelMap()[(bitmap.width * x).toInt(), (bitmap.height * y).toInt()]
            assertEquals((rgb shr 16 and 255) / 255f, color.red, 0.015f)
            assertEquals((rgb shr 8 and 255) / 255f, color.green, 0.015f)
            assertEquals((rgb and 255) / 255f, color.blue, 0.015f)
        }
        try {
            compose.setContent { MaterialTheme { Column {
                MapPanel("http://127.0.0.1:${server.address.port}", flying)
            } } }
            compose.runOnIdle { assertEquals(0, requests.get()) }
            compose.onNodeWithText("查看地图对象").performClick()
            waitForRetry()
            compose.onNodeWithContentDescription("地图对象位置与方向示意，不含底图").assertIsDisplayed()
            broken.set(false)
            compose.onNodeWithText("重试底图").performClick()
            waitForImage()
            assertPixel(0.1f, 0.3f, 0x224664)

            broken.set(true)
            current.set(fixture("map_tall"))
            waitForRetry()
            compose.onNodeWithContentDescription("地图底图与对象位置方向").assertDoesNotExist()
            broken.set(false)
            compose.onNodeWithText("重试底图").performClick()
            waitForImage()
            assertPixel(0.3f, 0.1f, 0x326941)

            compose.runOnIdle { flying = false }
            compose.onNodeWithText("等待有效飞行地图").assertIsDisplayed()
            compose.onNodeWithTag("map-objects-plot").assertDoesNotExist()
            val previousImages = images.get()
            compose.runOnIdle { flying = true }
            waitForImage()
            assertTrue(images.get() > previousImages)
            assertPixel(0.3f, 0.1f, 0x326941)
            compose.onNodeWithText("收起地图对象").performClick()
            compose.onNodeWithTag("map-objects-plot").assertDoesNotExist()
        } finally { server.stop(0) }
    }
}
