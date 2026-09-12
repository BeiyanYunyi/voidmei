package voidmei.config

import kotlin.test.*

class ModelWindowSettingsTest {
    @Test fun windowPreferencesRoundTripAndStayIndependentOfHudLayout() {
        val defaults = SettingsJson.decode("""{"version":1}""")
        assertFalse(defaults.modelWindowEnabled); assertTrue(defaults.modelWindowAlwaysOnTop)
        assertNull(defaults.modelWindowPosition)
        val configured = defaults.copy(modelWindowEnabled = true, modelWindowAlwaysOnTop = false, modelWindowPosition = WindowPosition(120f, 80f))
        assertEquals(configured, SettingsJson.decode(SettingsJson.encode(configured)))
        assertEquals(configured, configured.withHudLayout(AppSettings()))
    }
    @Test fun invalidBooleanAndPositionValuesAreRejected() {
        for (key in listOf("modelWindowEnabled", "modelWindowAlwaysOnTop")) {
            assertFails { SettingsJson.decode("""{"$key":"true"}""") }
            assertFails { SettingsJson.decode("""{"$key":1}""") }
        }
        assertFails { SettingsJson.decode("""{"modelWindowPosition":{"x":"bad","y":10}}""") }
    }
}
