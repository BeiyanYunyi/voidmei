package voidmei.config

import kotlin.test.*
import voidmei.telemetry.*

class HudEngineFieldsTest {
    @Test fun pressureUnitsUseOnlyTheGivenEngineAndKeepSubAtmosphericBoost() {
        val engine = Engine(2, null, null, null, null, null, null, manifoldPressureAtm = .5)
        assertEquals(.5, HudEngineField.MANIFOLD.value(engine))
        assertEquals(14.9606262, HudEngineField.MANIFOLD_INHG.value(engine)!!, .00001)
        assertEquals(-7.3479744, HudEngineField.BOOST_PSI.value(engine)!!, .00001)
        assertEquals(0.0, HudEngineField.BOOST_PSI.value(engine.copy(manifoldPressureAtm = 1.0)))
        for (field in listOf(HudEngineField.MANIFOLD, HudEngineField.MANIFOLD_INHG, HudEngineField.BOOST_PSI)) {
            for (bad in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
                assertNull(field.value(engine.copy(manifoldPressureAtm = bad)))
            }
        }
        val settings = AppSettings(hudEngineFields = listOf("boost_psi", "manifold_inhg"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(HudEngineField.BOOST_PSI, HudEngineField.MANIFOLD_INHG), HudEngineField.selected(settings.hudEngineFields))
        assertFalse("boost_psi" in HudEngineField.defaults || "manifold_inhg" in HudEngineField.defaults)
    }

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
