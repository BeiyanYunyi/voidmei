package voidmei.desktop

import com.sun.jna.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import voidmei.config.AppSettings
import java.nio.file.Files
import kotlin.test.*

class X11FocusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun x11SettingsCanEnableAndDisableFocusTracking() {
        assumeTrue(Platform.isLinux() && System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1")
        var settings by mutableStateOf(AppSettings())
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 800.dp).verticalScroll(rememberScrollState())) {
            HudSettingsPanel(settings) { settings = it }
        } } }
        compose.onNodeWithTag("hud-auto-hide-focus").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(settings.hudAutoHideOnFocusLoss) }
        compose.onNodeWithTag("hud-auto-hide-focus").performClick()
        compose.runOnIdle { assertFalse(settings.hudAutoHideOnFocusLoss) }
    }

    private interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer
        fun XCloseDisplay(display: Pointer): Int
        fun XDefaultRootWindow(display: Pointer): NativeLong
        fun XCreateSimpleWindow(display: Pointer, parent: NativeLong, x: Int, y: Int, width: Int, height: Int,
            border: Int, borderPixel: NativeLong, background: NativeLong): NativeLong
        fun XDestroyWindow(display: Pointer, window: NativeLong): Int
        fun XInternAtom(display: Pointer, name: String, onlyExisting: Int): NativeLong
        fun XChangeProperty(display: Pointer, window: NativeLong, property: NativeLong, type: NativeLong,
            format: Int, mode: Int, data: Pointer, count: Int): Int
        fun XDeleteProperty(display: Pointer, window: NativeLong, property: NativeLong): Int
        fun XSync(display: Pointer, discard: Int): Int
    }

    @Test fun readsNativePropertiesAndSurvivesDestroyedAndMalformedWindows() {
        assumeTrue(Platform.isLinux() && System.getenv("VOIDMEI_TEST_COMPOSITED_X11") == "1")
        val api = Native.load("X11", Xlib::class.java)
        val display = api.XOpenDisplay(null)
        val root = api.XDefaultRootWindow(display)
        val window = api.XCreateSimpleWindow(display, root, 0, 0, 10, 10, 0, NativeLong(0), NativeLong(0))
        val directory = Files.createTempDirectory("voidmei-focus")
        fun atom(name: String) = api.XInternAtom(display, name, 0)
        fun cardinal(target: NativeLong, name: String, type: Long, value: Long) {
            Memory(NativeLong.SIZE.toLong()).use { memory ->
                memory.setNativeLong(0, NativeLong(value))
                api.XChangeProperty(display, target, atom(name), NativeLong(type), 32, 0, memory, 1)
            }
            api.XSync(display, 0)
        }
        fun machine(value: String) {
            val bytes = value.toByteArray(Charsets.US_ASCII)
            Memory(bytes.size.toLong()).use { memory ->
                memory.write(0, bytes, 0, bytes.size)
                api.XChangeProperty(display, window, atom("WM_CLIENT_MACHINE"), NativeLong(31), 8, 0, memory, bytes.size)
            }
            api.XSync(display, 0)
        }
        var destroyed = false
        try {
            Files.createDirectory(directory.resolve("50"))
            Files.createSymbolicLink(directory.resolve("50/exe"), directory.resolve("aces"))
            cardinal(root, "_NET_ACTIVE_WINDOW", 33, window.toLong())
            cardinal(window, "_NET_WM_PID", 6, 50)
            machine("local-test-host")
            X11ForegroundProcess(directory, "local-test-host").use { source ->
                assertEquals(window.toLong(), source.activeWindow())
                assertEquals(50, source.processId(window.toLong()))
                assertEquals(GameFocus.GAME, detectGameFocus(source, setOf("aces")))
                Files.delete(directory.resolve("50/exe"))
                Files.createSymbolicLink(directory.resolve("50/exe"), directory.resolve("browser"))
                assertEquals(GameFocus.OTHER, detectGameFocus(source, setOf("aces")))
                Files.delete(directory.resolve("50/exe"))
                Files.createSymbolicLink(directory.resolve("50/exe"), directory.resolve("wine64-preloader"))
                assertEquals(GameFocus.UNKNOWN, detectGameFocus(source, setOf("aces", "aces.exe")))
                Files.delete(directory.resolve("50/exe"))
                Files.createSymbolicLink(directory.resolve("50/exe"), directory.resolve("browser"))
                machine("remote-host")
                assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
                machine("local-test-host")
                cardinal(window, "_NET_WM_PID", 33, 50) // Wrong property type.
                assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
                cardinal(window, "_NET_WM_PID", 6, 0xffffffffL)
                assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
                cardinal(window, "_NET_WM_PID", 6, 50)
                api.XDestroyWindow(display, window)
                destroyed = true
                api.XSync(display, 0)
                assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
                cardinal(root, "_NET_ACTIVE_WINDOW", 33, 0)
                assertNull(source.activeWindow())
                source.close()
                assertNull(source.activeWindow())
            }
        } finally {
            api.XDeleteProperty(display, root, atom("_NET_ACTIVE_WINDOW"))
            if (!destroyed) api.XDestroyWindow(display, window)
            api.XCloseDisplay(display)
            directory.toFile().deleteRecursively()
        }
    }
}
