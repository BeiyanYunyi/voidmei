package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*
import java.awt.Robot
import java.awt.image.BufferedImage
import javax.swing.JFrame
import kotlin.test.*

class HudMapNativeGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compatibleMapRefreshAndHideReplacePixelsInOneWindow() {
        check(System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1")
        val region = HudRegion("left", HudRegionContent.MAP, 0, 0, 320, 500, .5f)
        val scene = HudSceneLayout(700, 500, listOf(region, region.copy(id = "right", x = 380)))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = scene))
        val map = MutableStateFlow<MapConnection>(MapConnection.Available(hudPreviewMap()))
        var native: java.awt.Window? = null
        val background = JFrame("VoidMei map test background")
        val backgroundColor = java.awt.Color(20, 30, 60)
        try {
            compose.runOnIdle {
                background.isUndecorated = true
                background.isAlwaysOnTop = true
                background.contentPane.background = backgroundColor
                background.setBounds(50, 50, 760, 560)
                background.isVisible = true
            }
            compose.setContent {
                HudWindow(rememberWindowState(position = WindowPosition.Absolute(80.dp, 80.dp),
                    width = 700.dp, height = 500.dp), {}, compatibilityMode = true, clickThrough = true) {
                    SideEffect { native = window }
                    MaterialTheme { HudPanel(hudPreviewFlight(), settings, emptyList(), null, sharedMap = map) {} }
                }
            }
            compose.waitUntil(5000) { native?.isShowing == true }
            val window = assertNotNull(native)
            compose.runOnIdle { background.toFront(); window.toFront() }
            val robot = Robot()
            fun capture() = robot.createScreenCapture(window.bounds)
            // Only the player marker is yellow; text, grid and other objects use different colors.
            fun playerRightEdge(image: BufferedImage, start: Int): Int? {
                var count = 0
                var right = -1
                for (y in 0 until image.height) for (x in start until start + 320) {
                    val rgb = image.getRGB(x, y)
                    if ((rgb shr 16 and 255) > 200 && (rgb shr 8 and 255) > 200 && (rgb and 255) < 80) {
                        count++; right = maxOf(right, x)
                    }
                }
                return if (count > 30) right else null
            }
            compose.waitUntil(5000) { capture().let { playerRightEdge(it, 0) != null && playerRightEdge(it, 380) != null } }
            val first = capture()
            val leftX = assertNotNull(playerRightEdge(first, 0))
            val rightX = assertNotNull(playerRightEdge(first, 380))
            val output = java.io.File("build/hud-preview/map-regions-native.png")
            output.parentFile.mkdirs()
            javax.imageio.ImageIO.write(first, "png", output)
            compose.runOnIdle {
                val snapshot = hudPreviewMap()
                map.value = MapConnection.Available(snapshot.copy(objects = snapshot.objects.map {
                    if (it.icon == "Player") it.copy(position = MapPoint(.25, .5)) else it
                }))
            }
            compose.waitUntil(5000) { capture().let {
                (playerRightEdge(it, 0) ?: leftX) < leftX - 30 && (playerRightEdge(it, 380) ?: rightX) < rightX - 30
            } }
            compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene.copy(
                regions = listOf(region.copy(visible = false), scene.regions[1]))) }
            compose.waitUntil(5000) { capture().let {
                playerRightEdge(it, 380) != null && (0 until it.height).all { y ->
                    (0 until 320).all { x -> it.getRGB(x, y) == backgroundColor.rgb }
                }
            } }
            compose.runOnIdle { settings = settings.copy(hudSceneLayout = scene) }
            compose.waitUntil(5000) { playerRightEdge(capture(), 0) != null }
            compose.runOnIdle {
                assertSame(window, native)
                assertEquals(1, java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
                    .count { it.isDisplayable && it.title == "VoidMei HUD" })
            }
        } finally {
            compose.setContent {}
            compose.runOnIdle { background.dispose() }
        }
    }
}
