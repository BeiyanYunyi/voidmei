package voidmei.desktop

import com.sun.jna.Platform
import java.awt.EventQueue
import javax.swing.JFrame
import org.junit.Test
import kotlin.test.*

/** Run only on a dedicated macOS test desktop: this test foregrounds its own window. */
class NativeMacFocusTest {
    @Test fun foregroundApplicationMatchesTheOwnedJvmProcess() {
        check(Platform.isMac() && System.getenv("VOIDMEI_TEST_ISOLATED_MACOS") == "1") {
            "Use a dedicated macOS desktop with VOIDMEI_TEST_ISOLATED_MACOS=1"
        }
        lateinit var frame: JFrame
        EventQueue.invokeAndWait {
            frame = JFrame("VoidMei foreground detection test").apply {
                setSize(400, 200)
                setLocationRelativeTo(null)
                isVisible = true
                toFront()
                requestFocus()
            }
        }
        try {
            val pid = ProcessHandle.current().pid().toInt()
            val deadline = System.nanoTime() + 5_000_000_000L
            var found: MacForegroundApplication? = null
            while (System.nanoTime() < deadline) {
                found = MacAppKit.frontmostApplication()
                if (found?.pid == pid) break
                Thread.sleep(100)
            }
            assertEquals(pid, found?.pid, "AppKit must identify the foreground test process")
            val path = assertNotNull(found).executable
            assertTrue(java.io.File(path).isFile, "AppKit must return the executable path, not the app bundle")
            assertEquals(GameFocus.OTHER, detectMacGameFocus())
        } finally { EventQueue.invokeAndWait { frame.dispose() } }
    }
}
