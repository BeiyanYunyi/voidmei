package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import java.awt.Robot
import java.lang.management.ManagementFactory
import javax.swing.Timer
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

/** Measurements are observations, not a physical-GPU benchmark or a frame-rate assertion. */
class HudScenePerformanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun measureFullDisplayRepaints() {
        check(System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1")
        val display = hudDisplays().first { it.primary }
        val settings = AppSettings(hudSceneLayout = HudSceneLayout.initial(AppSettings(hudEngineIndex = 2)).copy(displayId = display.id))
        val base = hudPreviewFlight()
        var phase by mutableStateOf(false)
        var native: java.awt.Window? = null
        val tickTimes = mutableListOf<Long>()
        val timer = Timer(80) { phase = !phase; tickTimes += System.nanoTime() }
        try {
            compose.setContent {
                val state = rememberWindowState(width = 800.dp, height = 600.dp)
                HudWindow(state, {}, compatibilityMode = true, clickThrough = true) {
                    SideEffect { native = window }
                    updateHudWindowSize(window, state, 440, 600.dp, settings.hudSceneLayout)
                    MaterialTheme { Box(Modifier.fillMaxSize()) {
                        HudPanel(base.copy(telemetry = base.telemetry.copy(iasKmh = if (phase) 340.0 else 341.0)), settings, emptyList(), null) {}
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(if (phase) Color.Green else Color.Cyan, Offset(size.width / 2, size.height / 2), Size(24f, 24f))
                        }
                    } }
                }
            }
            compose.waitUntil(10000) { native?.bounds == display.bounds }
            val window = assertNotNull(native)
            val robot = Robot()
            fun pixel() = robot.getPixelColor(window.x + window.width / 2 + 8, window.y + window.height / 2 + 8)
            fun valid(c: java.awt.Color) = c.red <= 3 && c.green >= 252 && (c.blue <= 3 || c.blue >= 252)
            compose.waitUntil(10000) { valid(pixel()) }
            val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            val cpuStart = os.processCpuTime
            val started = System.nanoTime()
            var samples = 0
            var transitions = 0
            var previous: Int? = null
            var peakHeap = 0L
            compose.runOnIdle { timer.start() }
            compose.waitUntil(20000) {
                val color = pixel()
                assertTrue(valid(color), "Opaque marker disappeared: $color")
                assertEquals(display.bounds, window.bounds)
                assertSame(window, native)
                if (previous != null && previous != color.rgb) transitions++
                previous = color.rgb
                samples++
                peakHeap = maxOf(peakHeap, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                // Bound Robot sampling overhead instead of busy-polling the compositor.
                Thread.sleep(10)
                System.nanoTime() - started >= 5_000_000_000L
            }
            compose.runOnIdle { timer.stop() }
            val elapsed = System.nanoTime() - started
            val gaps = tickTimes.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.sorted()
            assertTrue(transitions >= 2 && gaps.isNotEmpty(), "The displayed frame must actually change")
            println("HUD_PERF size=${window.width}x${window.height} elapsedMs=${elapsed / 1_000_000} ticks=${tickTimes.size} samples=$samples transitions=$transitions " +
                "tickP50Ms=${gaps[gaps.size / 2]} tickP95Ms=${gaps[((gaps.size - 1) * .95).toInt()]} " +
                "processCpuPercent=${(os.processCpuTime - cpuStart) * 100.0 / elapsed} peakHeapMiB=${peakHeap / 1048576.0} " +
                "frameBufferMiB=${window.width.toLong() * window.height * 4 / 1048576.0}")
            println(rendererDiagnostics(window))
        } finally {
            compose.runOnIdle { timer.stop() }
            compose.setContent {}
        }
    }
}
