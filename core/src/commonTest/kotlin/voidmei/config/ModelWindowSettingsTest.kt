package voidmei.config

import kotlin.test.*

class ModelWindowSettingsTest {
    @Test fun windowPreferencesRoundTripAndStayIndependentOfHudLayout() {
        val defaults = SettingsJson.decode("""{"version":1}""")
        assertFalse(defaults.modelJetWindowEnabled); assertNull(defaults.modelJetWindowPosition)
        assertFalse(defaults.modelWindowHotkeyEnabled); assertFalse(defaults.modelWindowEnabled); assertTrue(defaults.modelWindowAlwaysOnTop)
        assertNull(defaults.modelWindowPosition)
        val configured = defaults.copy(modelJetWindowEnabled = true, modelJetWindowPosition = WindowPosition(200f, 100f), modelWindowHotkeyEnabled = true, modelWindowEnabled = true, modelWindowAlwaysOnTop = false, modelWindowPosition = WindowPosition(120f, 80f))
        assertEquals(configured, SettingsJson.decode(SettingsJson.encode(configured)))
        assertEquals(configured, configured.withHudLayout(AppSettings()))
    }
    @Test fun invalidBooleanAndPositionValuesAreRejected() {
        for (key in listOf("modelJetWindowEnabled", "modelWindowHotkeyEnabled", "modelWindowEnabled", "modelWindowAlwaysOnTop")) {
            assertFails { SettingsJson.decode("""{"$key":"true"}""") }
            assertFails { SettingsJson.decode("""{"$key":1}""") }
        }
        assertFails { SettingsJson.decode("""{"modelWindowPosition":{"x":"bad","y":10}}""") }
    }
}
