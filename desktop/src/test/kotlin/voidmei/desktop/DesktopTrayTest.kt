package voidmei.desktop

import org.junit.Test
import kotlin.test.*

class DesktopTrayTest {
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
