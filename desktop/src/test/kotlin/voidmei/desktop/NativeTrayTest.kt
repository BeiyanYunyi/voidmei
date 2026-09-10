package voidmei.desktop

import org.junit.Test
import java.awt.EventQueue
import java.awt.SystemTray
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
        try {
            waitFor { SystemTray.isSupported() }
            var initial = 0
            EventQueue.invokeAndWait {
                initial = SystemTray.getSystemTray().trayIcons.size
                registration = assertNotNull(installDesktopTray({}, {}, {}) { available += it })
                assertEquals(initial + 1, SystemTray.getSystemTray().trayIcons.size)
            }
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
            EventQueue.invokeAndWait { registration?.close() }
            stop(process)
        }
    }
}
