package voidmei.desktop

import org.junit.Test
import java.awt.EventQueue
import java.awt.SystemTray
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.LinkedBlockingQueue
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Memory
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.*

class NativeTrayTest {
    @Test fun nativeManagerLossRecoveryAndDisposal() {
        require(System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1")
        val manager = requireNotNull(System.getenv("VOIDMEI_TRAY_MANAGER"))
        val logs = Files.createDirectories(java.nio.file.Path.of("build", "native-tray"))
        val log = Files.createTempFile(logs, "manager-", ".log")
        fun start() = ProcessBuilder(manager).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start()
        fun stop(process: Process) {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(3, TimeUnit.SECONDS)) }
        }
        fun waitFor(condition: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (!condition() && System.nanoTime() < end) Thread.sleep(25)
            assertTrue(condition(), "Timed out; tray manager log: $log")
        }
        var process = start()
        var registration: AutoCloseable? = null
        val available = CopyOnWriteArrayList<Boolean>()
        var main: Frame? = null
        try {
            waitFor { SystemTray.isSupported() }
            var initial = 0
            EventQueue.invokeAndWait {
                initial = SystemTray.getSystemTray().trayIcons.size
                main = Frame("Native tray restore test").apply { setSize(320, 180); isVisible = true; isVisible = false }
                registration = assertNotNull(installDesktopTray({ restoreDesktopWindow(main!!) }, {}, {}) { available += it })
                assertEquals(initial + 1, SystemTray.getSystemTray().trayIcons.size)
            }
            checkProxyClicks(main!!)
            stop(process)
            waitFor { false in available }
            process = start()
            waitFor { available.lastOrNull() == true }
            EventQueue.invokeAndWait {
                registration!!.close(); registration = null
                assertEquals(initial, SystemTray.getSystemTray().trayIcons.size)
            }
            val events = available.size
            stop(process)
            waitFor { !SystemTray.isSupported() }
            EventQueue.invokeAndWait { assertEquals(events, available.size) }
        } finally {
            EventQueue.invokeAndWait { registration?.close(); main?.dispose() }
            stop(process)
        }
    }

    /** Reproduce KDE: XSendEvent targets the outer XEmbed window, not the AWT Canvas. */
    private fun checkProxyClicks(main: Frame) {
        val clicks = LinkedBlockingQueue<Int>()
        val popups = LinkedBlockingQueue<Boolean>()
        val xlib = Native.load("X11", X11TrayEventBridge.Xlib::class.java)
        val display = assertNotNull(xlib.XOpenDisplay(null))
        lateinit var tray: Frame
        val icon = SystemTray.getSystemTray().trayIcons.single()
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) { clicks.add(event.button) }
            override fun mousePressed(event: MouseEvent) { popups.add(event.isPopupTrigger) }
        }
        try {
            EventQueue.invokeAndWait {
                tray = Frame.getFrames().single {
                    it.isDisplayable && it.javaClass.name == "sun.awt.X11.XTrayIconPeer\$XTrayIconEmbeddedFrame"
                }
                icon.addMouseListener(listener)
            }
            fun proxyClick(button: Int, time: Long) {
                Memory(24L * Native.LONG_SIZE).use { buffer ->
                    buffer.clear()
                    val event = X11TrayEventBridge.ButtonEvent(buffer)
                    event.display = display
                    event.window = NativeLong(Native.getWindowID(tray))
                    val attributes = X11TrayEventBridge.Attributes()
                    assertNotEquals(0, xlib.XGetWindowAttributes(display, event.window, attributes))
                    event.root = attributes.root
                    event.button = button
                    event.time = NativeLong(time)
                    event.x = 12; event.y = 12
                    event.xRoot = 12; event.yRoot = 12
                    event.sameScreen = 1
                    for (type in 4..5) {
                        event.type = type
                        event.write()
                        assertNotEquals(0, xlib.XSendEvent(display, event.window, 0,
                            NativeLong(if (type == 4) 4 else 8), buffer))
                    }
                    xlib.XFlush(display)
                }
            }
            repeat(2) { attempt ->
                proxyClick(MouseEvent.BUTTON1, (attempt + 1) * 1000L)
                assertEquals(MouseEvent.BUTTON1, clicks.poll(5, TimeUnit.SECONDS))
                assertEquals(false, popups.poll(5, TimeUnit.SECONDS))
                // Restoring the Compose window is asynchronous in production too.
                EventQueue.invokeAndWait { assertTrue(main.isVisible); main.isVisible = false }
            }
            proxyClick(MouseEvent.BUTTON3, 3000)
            assertEquals(MouseEvent.BUTTON3, clicks.poll(5, TimeUnit.SECONDS))
            assertEquals(true, popups.poll(5, TimeUnit.SECONDS), "Right click must retain AWT popup semantics")
            EventQueue.invokeAndWait { assertFalse(main.isVisible) }
        } finally {
            EventQueue.invokeAndWait { icon.removeMouseListener(listener) }
            xlib.XCloseDisplay(display)
        }
    }
}
