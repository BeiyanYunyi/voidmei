package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import voidmei.config.AppSettings
import voidmei.telemetry.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Robot
import javax.swing.JFrame
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

/** Opt-in pixel sampling of test-owned windows on a composited display. */
class NativeHudTransparencyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compatibleHudRetainsOpaquePixelsDuringContinuousRepaint() {
        check(System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1")
        var native: java.awt.Window? = null
        var phase by mutableStateOf(false)
        var mainWindow: java.awt.Window? = null
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380,"H, m":91,"Mfuel, kg":220}""", """{"valid":true,"type":"p-51c-10-nt"}""")!!
        val background = JFrame("VoidMei repaint test background")
        val timer = javax.swing.Timer(100) {
            phase = !phase
            mainWindow?.setLocation(if (phase) 520 else 540, 80)
        }
        try {
            compose.runOnIdle {
                background.isUndecorated = true
                background.isAlwaysOnTop = true
                background.contentPane.background = java.awt.Color.RED
                background.setBounds(50, 50, 400, 250)
                background.isVisible = true
            }
            compose.setContent {
                Window(onCloseRequest = {}, title = "VoidMei moving main-window regression",
                    state = rememberWindowState(position = WindowPosition.Absolute(520.dp, 80.dp), width = 300.dp, height = 200.dp)) {
                    SideEffect { mainWindow = window }
                    MaterialTheme { Text("IAS ${if (phase) 380 else 381}") }
                }
                SwingGraphicsHudWindow(rememberWindowState(position = WindowPosition.Absolute(80.dp, 80.dp),
                    width = 240.dp, height = 120.dp), {}) {
                    SideEffect { native = window }
                    Box(Modifier.fillMaxSize()) {
                    MaterialTheme {
                        HudPanel(ConnectionState.Flying(telemetry.copy(iasKmh = if (phase) 380.0 else 381.0), FlightMetrics()),
                            AppSettings(hudAttitude = false, hudMechanization = false), emptyList(), null) { Text("HUD") }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(if (phase) Color.Green else Color.Cyan,
                            Offset(size.width / 3, 0f), Size(size.width * 2 / 3, size.height))
                    }
                    }
                }
            }
            compose.waitUntil(5000) { native?.isVisible == true }
            val window = assertNotNull(native)
            System.getenv("VOIDMEI_TEST_EXPECT_RENDERER")?.let { expected ->
                compose.waitUntil(5000) { rendererDiagnostics(window).contains("绘制后端：$expected") }
            }
            val robot = Robot()
            compose.runOnIdle { background.toFront(); window.toFront() }
            fun sample() = robot.getPixelColor(window.x + window.width * 3 / 4, window.y + window.height / 2)
            fun opaque(c: java.awt.Color) = c.red <= 3 && c.green >= 252 && (c.blue <= 3 || c.blue >= 252)
            compose.waitUntil(5000) { robot.getPixelColor(60, 60) == java.awt.Color.RED && opaque(sample()) }
            val originalBounds = window.bounds
            val mainPositions = mutableSetOf<Int>()
            val colors = mutableSetOf<Int>()
            val unexpected = mutableMapOf<Int, Int>()
            var samples = 0
            compose.runOnIdle { timer.start() }
            val started = System.nanoTime()
            compose.waitUntil(25000) {
                mainWindow?.let { mainPositions += it.x }
                val pixel = sample()
                colors += pixel.rgb
                if (!opaque(pixel)) unexpected[pixel.rgb] = (unexpected[pixel.rgb] ?: 0) + 1
                assertEquals(originalBounds, window.bounds)
                assertSame(window, native)
                assertTrue(window.isVisible)
                ++samples >= 200 && System.nanoTime() - started >= 15_000_000_000L
            }
            println("Continuous HUD repaint: $samples pixel samples over ${(System.nanoTime() - started) / 1_000_000} ms; colors=$colors unexpected=$unexpected")
            assertTrue(mainPositions.size >= 2, "Main-window movement must occur during HUD updates")
            assertTrue(colors.size >= 2, "Repaint must actually change the sampled color")
            assertTrue(unexpected.isEmpty(), "Opaque HUD region vanished during repaint: $unexpected / $samples samples")
        } finally {
            compose.runOnIdle { timer.stop() }
            compose.setContent {}
            compose.runOnIdle { background.dispose() }
        }
    }

    @Test fun transparentAndTranslucentPixelsRevealChangingBackground() {
        check(System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1" ||
            System.getenv("VOIDMEI_TEST_DESKTOP_PIXELS") == "1")
        var native: java.awt.Window? = null
        var opaqueBand by mutableStateOf(true)
        val background = JFrame("VoidMei transparency test background")
        try {
            compose.runOnIdle {
                background.isUndecorated = true
                background.isAlwaysOnTop = true
                background.contentPane.background = java.awt.Color.RED
                background.setBounds(50, 50, 400, 250)
                background.isVisible = true
            }
            compose.setContent {
                val state = rememberWindowState(position = WindowPosition.Absolute(80.dp, 80.dp),
                    width = 240.dp, height = 120.dp)
                SwingGraphicsHudWindow(state, {}) {
                    SideEffect { native = window }
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Green.copy(alpha = 0.5f), Offset(size.width / 3, 0f), Size(size.width / 3, size.height))
                        if (opaqueBand) drawRect(Color.Green, Offset(2 * size.width / 3, 0f), Size(size.width / 3, size.height))
                    }
                }
            }
            compose.waitUntil(5000) { native?.isVisible == true }
            val window = assertNotNull(native)
            System.getenv("VOIDMEI_TEST_EXPECT_RENDERER")?.let { expected ->
                compose.waitUntil(5000) { rendererDiagnostics(window).contains("绘制后端：$expected") }
            }
            compose.runOnIdle {
                println("Transparency test renderer: ${rendererDiagnostics(window)}")
                background.toFront()
                window.toFront()
            }
            val robot = Robot()
            fun checkBackground(expected: java.awt.Color) {
                var actual = java.awt.Color.BLACK
                try {
                    compose.waitUntil(5000) {
                        actual = robot.getPixelColor(background.x + 10, background.y + 10)
                        actual == expected
                    }
                } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                    throw AssertionError("Background control pixel unavailable or occluded: expected=$expected actual=$actual; HUD alpha cannot be assessed", failure)
                }
            }
            checkBackground(java.awt.Color.RED)
            compose.runOnIdle {
                fun describe(component: java.awt.Component) {
                    println("Alpha component: ${component.javaClass.name} background=${component.background} opaque=${(component as? javax.swing.JComponent)?.isOpaque}")
                    (component as? java.awt.Container)?.components?.forEach { describe(it) }
                }
                describe(window)
            }
            fun checkPixels(expected: List<java.awt.Color>) {
                var actual = emptyList<java.awt.Color>()
                try {
                    compose.waitUntil(5000) {
                        actual = (0..2).map { band ->
                            robot.getPixelColor(window.x + window.width * (2 * band + 1) / 6, window.y + window.height / 2)
                        }
                        actual.zip(expected).all { (a, e) ->
                            abs(a.red - e.red) <= 3 && abs(a.green - e.green) <= 3 && abs(a.blue - e.blue) <= 3
                        }
                    }
                } finally { println("Transparent HUD pixels: actual=$actual expected=$expected; background control=${robot.getPixelColor(60,60)}") }
            }
            checkPixels(listOf(java.awt.Color.RED, java.awt.Color(127, 128, 0), java.awt.Color.GREEN))
            compose.runOnIdle {
                background.contentPane.background = java.awt.Color.BLUE
                background.repaint()
            }
            checkBackground(java.awt.Color.BLUE)
            checkPixels(listOf(java.awt.Color.BLUE, java.awt.Color(0, 128, 127), java.awt.Color.GREEN))
            compose.runOnIdle { opaqueBand = false }
            checkPixels(listOf(java.awt.Color.BLUE, java.awt.Color(0, 128, 127), java.awt.Color.BLUE))
            compose.runOnIdle { opaqueBand = true }
            checkPixels(listOf(java.awt.Color.BLUE, java.awt.Color(0, 128, 127), java.awt.Color.GREEN))
        } finally {
            compose.setContent {}
            compose.runOnIdle { background.dispose() }
        }
    }
}
