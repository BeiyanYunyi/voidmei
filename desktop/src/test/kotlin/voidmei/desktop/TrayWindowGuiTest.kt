package voidmei.desktop

import java.awt.EventQueue
import java.awt.Frame
import org.junit.Test
import kotlin.test.*

class TrayWindowGuiTest {
    @Test fun restoreShowsHiddenWindowAndRaisesAlreadyVisibleWindow() {
        EventQueue.invokeAndWait {
            var raises = 0
            var focusRequests = 0
            val window = object : Frame("VoidMei tray restore test") {
                override fun toFront() { raises++; super.toFront() }
                override fun requestFocus() { focusRequests++; super.requestFocus() }
            }
            try {
                window.setSize(320, 180)
                window.isVisible = true
                window.isVisible = false
                val beforeRaises = raises
                val beforeFocus = focusRequests
                restoreDesktopWindow(window)
                assertTrue(window.isVisible)
                assertTrue(raises > beforeRaises)
                assertTrue(focusRequests > beforeFocus)
                val visibleRaises = raises
                restoreDesktopWindow(window)
                assertTrue(raises > visibleRaises, "An already visible window must also be raised")
                window.extendedState = Frame.ICONIFIED
                restoreDesktopWindow(window)
                assertEquals(0, window.extendedState and Frame.ICONIFIED)
                window.dispose()
                val disposedRaises = raises
                restoreDesktopWindow(window)
                assertFalse(window.isDisplayable)
                assertEquals(disposedRaises, raises)
            } finally { window.dispose() }
        }
    }
}
