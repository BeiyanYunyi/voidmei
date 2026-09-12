package voidmei.desktop

import org.junit.Test
import kotlin.test.*

class DesktopTrayTest {
    @Test fun trayLeftClickRestoresButContextMenuAndMiddleClickDoNot() {
        java.awt.EventQueue.invokeAndWait {
            var shows = 0
            val listener = TrayShowMouseListener { shows++ }
            val source = java.awt.Canvas()
            fun click(button: Int, count: Int = 1, popup: Boolean = false) =
                listener.mouseClicked(java.awt.event.MouseEvent(source,
                    java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, count, popup, button))
            click(java.awt.event.MouseEvent.BUTTON1)
            assertEquals(1, shows, "A single left click must restore the window")
            click(java.awt.event.MouseEvent.BUTTON1, count = 2)
            assertEquals(2, shows)
            click(java.awt.event.MouseEvent.BUTTON2)
            click(java.awt.event.MouseEvent.BUTTON3, popup = true)
            click(java.awt.event.MouseEvent.BUTTON1, popup = true)
            assertEquals(2, shows, "Opening the context menu must not restore the window")
        }
    }

    @Test fun nativeAvailabilityEventsRestoreAccessAndIgnoreDisposedCallbacks() {
        val states = mutableListOf<Boolean>()
        val listener = TrayAvailabilityListener {
            assertTrue(java.awt.EventQueue.isDispatchThread())
            states += it
        }
        fun event(name: String, newValue: Any?) = java.beans.PropertyChangeEvent(this, name, null, newValue)
        listener.propertyChange(event("trayIcons", null))
        listener.propertyChange(event("systemTray", null))
        listener.propertyChange(event("systemTray", Any()))
        java.awt.EventQueue.invokeAndWait { assertEquals(listOf(false, true), states) }
        java.awt.EventQueue.invokeAndWait {
            listener.propertyChange(event("systemTray", null))
            listener.close()
        }
        listener.propertyChange(event("systemTray", Any()))
        java.awt.EventQueue.invokeAndWait { assertEquals(listOf(false, true), states) }
    }

    @Test fun backgroundStartupRequiresPreferenceAndRecoveryEntry() {
        for (preference in listOf(false, true)) for (available in listOf(false, true))
            for (recovery in listOf(false, true)) for (error in listOf(false, true)) {
                val hidden = shouldStartInTray(preference, available, recovery, error)
                if (!preference || !available || recovery || error) assertFalse(hidden)
                else assertTrue(hidden)
            }
    }
}
