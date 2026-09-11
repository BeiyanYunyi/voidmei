package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class TemperatureReadingTest {
    private fun telemetry(indicators: String) = TelemetryParser.parse("""{"valid":true,
        "water temp 1, C":90,"oil temp 1, C":80,"water temp 2, C":190,"oil temp 2, C":180}""",
        """{"valid":true,$indicators}""")!!

    @Test fun cockpitValuesKeepTheirSourceAndFallbackOrder() {
        val all = telemetry(""""water_temperature":100,"head_temperature":200,"oil_temperature":70""")
        assertEquals(TemperatureReading(100.0, "水温仪表原值"), all.displayTemperature(false))
        assertEquals(TemperatureReading(70.0, "油温仪表原值"), all.displayTemperature(true))
        val head = all.copy(waterTemperatureRaw = null, oilTemperatureRaw = null)
        assertEquals(TemperatureReading(200.0, "缸温仪表原值"), head.displayTemperature(false))
        assertEquals(TemperatureReading(80.0, "°C · 1号", engineIndex = 1), head.displayTemperature(true))
        val state = head.copy(headTemperatureRaw = null, engines = head.engines.reversed())
        assertEquals(TemperatureReading(90.0, "°C · 1号", engineIndex = 1), state.displayTemperature(false))
        assertNull(state.copy(engines = state.engines.filter { it.index == 2 }).displayTemperature(false))
        assertNull(state.copy(engines = state.engines + state.engines.single { it.index == 1 }).displayTemperature(true))
    }

    @Test fun sentinelsAndWrongTypesFallBackButZeroAndNegativeReadingsRemainValid() {
        val missing = telemetry(""""water_temperature":-65535,"head_temperature":-65534,"oil_temperature":"100"""")
        assertNull(missing.waterTemperatureRaw)
        assertNull(missing.headTemperatureRaw)
        assertNull(missing.oilTemperatureRaw)
        assertEquals(90.0, missing.displayTemperature(false)?.value)
        val valid = telemetry(""""water_temperature":0,"oil_temperature":-20""")
        assertEquals(0.0, valid.displayTemperature(false)?.value)
        assertEquals(-20.0, valid.displayTemperature(true)?.value)
        assertEquals("水温仪表原值", HudField.ENGINE_TEMPERATURE.unitFor(valid))
        assertEquals("°C · 1号", HudField.ENGINE_TEMPERATURE.unitFor(missing))
    }

    @Test fun legacyTemperatureChoicesMergeAndPersist() {
        val imported = LegacySettingsReader.read("""(panel p
            (item water :type data :target getWaterTemp :value true)
            (item oil :type data :target getOilTemp :value false))""")
        val updated = imported.applyTo(AppSettings(hudFields = listOf("future", "oil_temperature")))
        assertEquals(listOf("future", "engine_temperature"), updated.hudFields)
        assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
    }
}
