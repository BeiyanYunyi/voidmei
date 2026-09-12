package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import java.awt.Point
import java.awt.Robot
import java.awt.event.InputEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

/** Native pointer injection requires a dedicated test desktop, never the user's active session. */
class NativeHudPointerTest {
    @get:Rule val compose = createComposeRule()

    private fun requireIsolatedDesktop() {
        check((com.sun.jna.Platform.isLinux() && System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1") ||
            (com.sun.jna.Platform.isWindows() && System.getenv("VOIDMEI_TEST_ISOLATED_WINDOWS") == "1") ||
            (com.sun.jna.Platform.isMac() && System.getenv("VOIDMEI_TEST_ISOLATED_MACOS") == "1")) {
            "Use a dedicated Xvfb display or an isolated interactive Windows/macOS test desktop"
        }
        System.getProperty("voidmei.testPackagedApp")?.let { directory ->
            val packaged = java.io.File(directory, "lib/app").canonicalFile.toPath()
            for (name in listOf("voidmei.desktop.HudWindowKt", "voidmei.desktop.HudPointerController",
                "voidmei.config.AppSettings", "com.sun.jna.Native", "org.jetbrains.skiko.SkiaLayer")) {
                val origin = java.io.File(Class.forName(name).protectionDomain.codeSource.location.toURI())
                    .canonicalFile.toPath()
                assertTrue(origin.startsWith(packaged) && origin.toString().endsWith(".jar"),
                    "$name must load from the package, but loaded from $origin")
            }
        }
    }

    @Test fun nonFocusableHudStillAcceptsMouseClose() = checkPointer(false)

    @Test fun swingGraphicsHudResizesMovesAndAcceptsMouseClose() = checkPointer(true)

    @Test fun composeHudPassesClicksThroughAndRestoresInput() = checkClickThrough(false)

    @Test fun swingGraphicsHudPassesClicksThroughAndRestoresInput() = checkClickThrough(true)

    @Test fun composeHudStartsWithSavedClickThrough() = checkClickThrough(false, true)

    @Test fun swingGraphicsHudStartsWithSavedClickThrough() = checkClickThrough(true, true)

    private fun checkClickThrough(compatible: Boolean, initiallyEnabled: Boolean = false) {
        requireIsolatedDesktop()
        val lowerClicks = java.util.concurrent.atomic.AtomicInteger()
        val hudClicks = java.util.concurrent.atomic.AtomicInteger()
        val inputChanges = java.util.concurrent.atomic.AtomicInteger()
        var clickThrough by mutableStateOf(initiallyEnabled)
        var generation by mutableStateOf(0)
        var shown by mutableStateOf(true)
        var native: java.awt.Window? = null
        lateinit var lower: javax.swing.JFrame
        java.awt.EventQueue.invokeAndWait {
            lower = javax.swing.JFrame("Owned click-through test background").apply {
                isUndecorated = true
                contentPane.add(javax.swing.JButton("Background").apply {
                    addActionListener { lowerClicks.incrementAndGet() }
                })
                setBounds(60, 60, 600, 400)
                isVisible = true
            }
        }
        try {
            compose.setContent {
                key(generation) {
                    HudWindow(rememberWindowState(width = 300.dp, height = 160.dp), {},
                        compatibilityMode = compatible, clickThrough = clickThrough, visible = shown,
                        onPointerError = { error ->
                            assertNull(error)
                            inputChanges.incrementAndGet()
                        }) {
                        SideEffect { native = window }
                        TextButton(onClick = { hudClicks.incrementAndGet() }, modifier = Modifier.fillMaxSize()) {
                            Text("Owned HUD button")
                        }
                    }
                }
            }
            compose.waitUntil(5000) { native?.isVisible == true }
            val window = assertNotNull(native)
            compose.runOnIdle { window.setLocation(100, 100) }
            val robot = Robot().apply { autoDelay = 60 }
            fun click() {
                robot.mouseMove(200, 180)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                robot.waitForIdle()
            }
            if (initiallyEnabled) {
                compose.waitUntil(5000) { inputChanges.get() == 1 }
                click()
                compose.waitUntil(5000) { lowerClicks.get() == 1 }
                assertEquals(0, hudClicks.get())
                compose.runOnIdle { clickThrough = false }
                compose.waitUntil(5000) { inputChanges.get() == 2 }
                lowerClicks.set(0)
                inputChanges.set(0)
            }
            // Verify the interactive overlay receives real input before trying native changes.
            click()
            compose.waitUntil(5000) { hudClicks.get() == 1 }
            assertEquals(0, lowerClicks.get())
            repeat(2) { cycle ->
                compose.runOnIdle { clickThrough = true }
                compose.waitUntil(5000) { inputChanges.get() == cycle * 2 + 1 }
                click()
                compose.waitUntil(5000) { lowerClicks.get() == cycle + 1 }
                assertEquals(cycle + 1, hudClicks.get())
                compose.runOnIdle { clickThrough = false }
                compose.waitUntil(5000) { inputChanges.get() == cycle * 2 + 2 }
                click()
                compose.waitUntil(5000) { hudClicks.get() == cycle + 2 }
                assertEquals(cycle + 1, lowerClicks.get())
            }
            compose.runOnIdle { clickThrough = true }
            compose.waitUntil(5000) { inputChanges.get() == 5 }
            compose.runOnIdle { shown = false }
            compose.waitUntil(5000) { !window.isVisible }
            assertTrue(window.isDisplayable)
            click()
            compose.waitUntil(5000) { lowerClicks.get() == 3 }
            compose.runOnIdle { shown = true }
            compose.waitUntil(5000) { window.isVisible }
            click()
            compose.waitUntil(5000) { lowerClicks.get() == 4 }
            compose.runOnIdle { generation++ }
            compose.waitUntil(5000) { !window.isDisplayable && native !== window && native?.isVisible == true }
            compose.runOnIdle { assertNotNull(native).setLocation(100, 100) }
            click()
            compose.waitUntil(5000) { lowerClicks.get() == 5 }
            assertEquals(3, hudClicks.get())
            val changesBeforeRestore = inputChanges.get()
            compose.runOnIdle { clickThrough = false }
            compose.waitUntil(5000) { inputChanges.get() > changesBeforeRestore }
            click()
            compose.waitUntil(5000) { hudClicks.get() == 4 }
            assertEquals(5, lowerClicks.get())
        } finally {
            java.awt.EventQueue.invokeAndWait { lower.dispose() }
        }
    }

    private fun checkPointer(swingGraphics: Boolean) {
        requireIsolatedDesktop()
        var visible by mutableStateOf(true)
        var native: java.awt.Window? = null
        var preferredWidth by mutableStateOf(440)
        var expectedWidth = 0
        var savedState: androidx.compose.ui.window.WindowState? = null
        compose.setContent {
            val state = rememberWindowState(width = 440.dp, height = 160.dp)
            savedState = state
            val content: @Composable androidx.compose.ui.window.WindowScope.() -> Unit = {
                updateHudWindowSize(window, state, preferredWidth, 160.dp)
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                SideEffect {
                    native = window
                    val config = window.graphicsConfiguration
                    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
                    expectedWidth = minOf((preferredWidth * density / config.defaultTransform.scaleX).toInt(),
                        config.bounds.width - insets.left - insets.right)
                }
                Column(Modifier.fillMaxSize()) {
                    HudDraggableArea(Modifier.fillMaxWidth().height(40.dp)) {
                        Box(Modifier.fillMaxSize()) { Text("拖动测试 HUD") }
                    }
                    TextButton(onClick = { visible = false }, modifier = Modifier.fillMaxWidth().weight(1f)) { Text("关闭测试 HUD") }
                }
            }
            if (visible) {
                if (swingGraphics) SwingGraphicsHudWindow(state, {}, content = content)
                else HudWindow(state, {}, content = content)
            }
        }
        compose.waitUntil(5000) { native?.isVisible == true }
        // ComposePanel owns a separate composition, outside this rule's semantics root.
        // The actual Robot click below verifies that its button is rendered and interactive.
        if (!swingGraphics) compose.onNodeWithText("关闭测试 HUD").assertExists()
        val window = assertNotNull(native)
        compose.runOnIdle {
            val scale = window.graphicsConfiguration.defaultTransform.scaleX
            System.getProperty("sun.java2d.uiScale")?.toDoubleOrNull()?.let { assertEquals(it, scale) }
            println("HUD native test: swingGraphics=$swingGraphics, actual AWT scale=$scale")
        }
        var expectedX = 0f
        var expectedY = 0f
        compose.runOnIdle {
            window.setLocation(80, 90)
            // At this isolated display's default density, dp and AWT coordinates coincide.
            expectedX = 80f
            expectedY = 90f
        }
        compose.waitUntil(5000) {
            val position = savedState?.position as? androidx.compose.ui.window.WindowPosition.Absolute
            position != null && position.x.value == expectedX && position.y.value == expectedY
        }
        for (width in listOf(680, 240, 1000)) {
            compose.runOnIdle { expectedWidth = 0; preferredWidth = width }
            compose.waitForIdle()
            compose.waitUntil(5000) { window.width == expectedWidth && expectedWidth > 0 }
            compose.runOnIdle {
                assertFalse(window.isFocusableWindow)
                val config = window.graphicsConfiguration
                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
                assertTrue(window.width <= config.bounds.width - insets.left - insets.right)
            }
        }
        var point = Point()
        compose.runOnIdle {
            assertFalse(window.isFocusableWindow)
            point = window.locationOnScreen.let { Point(it.x + window.width / 2, it.y + window.height / 2) }
        }
        val robot = Robot()
        try {
            robot.waitForIdle()
            val origin = window.locationOnScreen
            robot.mouseMove(origin.x + 60, origin.y + 20)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(80)
            for (step in 1..5) {
                robot.mouseMove(origin.x + 60 + step * 10, origin.y + 20 + step * 8)
                robot.delay(40)
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            compose.waitUntil(5000) { window.x == origin.x + 50 && window.y == origin.y + 40 }
            compose.waitUntil(5000) {
                val position = savedState?.position as? androidx.compose.ui.window.WindowPosition.Absolute
                position?.x?.value == window.x.toFloat() && position.y.value == window.y.toFloat()
            }
            val moved = window.location
            point = Point(moved.x + window.width / 2, moved.y + window.height / 2)
            robot.mouseMove(point.x, point.y)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            compose.waitUntil(5000) { !window.isDisplayable }
            compose.runOnIdle { native = null; visible = true }
            compose.waitUntil(5000) { native?.isVisible == true }
            val reopened = assertNotNull(native)
            compose.waitUntil(5000) { reopened.location == moved && reopened.width == expectedWidth }
            compose.runOnIdle {
                assertNotSame(window, reopened)
                assertFalse(reopened.isFocusableWindow)
                visible = false
            }
            compose.waitUntil(5000) { !reopened.isDisplayable }
        } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK) }
    }
}
