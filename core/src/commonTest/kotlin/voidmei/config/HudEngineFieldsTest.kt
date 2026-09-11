package voidmei.config

import kotlin.test.*
import voidmei.telemetry.*

class HudEngineFieldsTest {
    @Test fun selectionPersistsOrderAndUnknownIdsWithoutInventingReadings() {
        assertEquals(HudEngineField.defaults, SettingsJson.decode("""{"version":1}""").hudEngineFields)
        val settings = AppSettings(hudEngineFields = listOf("oil_temperature", "future", "throttle"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(HudEngineField.OIL_TEMPERATURE, HudEngineField.THROTTLE), HudEngineField.selected(settings.hudEngineFields))
        assertTrue(HudEngineField.selected(emptyList()).isEmpty())
        for (json in listOf("null", "true", "[1]", "[null]")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudEngineFields":$json}""") }
        }
        assertEquals(0.0, HudEngineField.THROTTLE.value(Engine(2, 0.0, null, null, null, null, null)))
        assertNull(HudEngineField.RPM.value(Engine(2, null, null, null, null, null, null)))
        assertNull(HudEngineField.RPM.value(Engine(2, null, Double.NaN, null, null, null, null)))
    }
}
