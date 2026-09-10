package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*

class HudAltitudeModeTest {
    private val t = TelemetryParser.parse("""{"valid":true,"H, m":1200}""", """{"valid":true}""")!!
    private fun flight(radar: Double?, unit: CockpitAltitudeUnit? = CockpitAltitudeUnit.METRES) =
        ConnectionState.Flying(t.copy(radioAltitudeRaw = radar), FlightMetrics(cockpitAltitudeUnit = unit))

    @Test fun thresholdUsesConvertedMetresAndMissingRadarFallsBackToSeaLevel() {
        assertEquals(HudAltitudeReading(1200.0, false), HudAltitudeMode.SEA_LEVEL.reading(flight(10.0)))
        for (height in listOf(0.0, 499.0, 500.0))
            assertEquals(HudAltitudeReading(height, true), HudAltitudeMode.LOW_RADAR.reading(flight(height)))
        assertEquals(HudAltitudeReading(1200.0, false), HudAltitudeMode.LOW_RADAR.reading(flight(500.01)))
        assertEquals(HudAltitudeReading(900.0, true), HudAltitudeMode.ALWAYS_RADAR.reading(flight(900.0)))
        assertTrue(HudAltitudeMode.LOW_RADAR.reading(flight(1000.0, CockpitAltitudeUnit.FEET)).radarEstimated)
        for (mode in HudAltitudeMode.entries) {
            for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
                assertEquals(HudAltitudeReading(1200.0, false), mode.reading(flight(invalid)))
            assertEquals(HudAltitudeReading(1200.0, false), mode.reading(flight(20.0, null)))
        }
        assertEquals(HudAltitudeReading(null, false), HudAltitudeMode.ALWAYS_RADAR.reading(
            ConnectionState.Flying(t.copy(altitudeM = null), FlightMetrics())))
    }

    @Test fun settingsAreStrictAndLegacyFalseMeansLowAltitudeSwitching() {
        for (mode in HudAltitudeMode.entries) {
            val settings = AppSettings(hudAltitudeMode = mode)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(HudAltitudeMode.SEA_LEVEL, SettingsJson.decode("""{"version":1}""").hudAltitudeMode)
        for (value in listOf("null", "true", "3", "\"unknown\""))
            assertFails { SettingsJson.decode("""{"version":1,"hudAltitudeMode":$value}""") }
        for ((value, mode) in listOf(true to HudAltitudeMode.ALWAYS_RADAR, false to HudAltitudeMode.LOW_RADAR)) {
            val imported = LegacySettingsReader.read("""(panel p (item radar :type switch :target alwaysShowRadarAltitude :value $value))""")
            val current = AppSettings(hudFields = emptyList(), hudAttitude = false)
            assertEquals(current.copy(hudAltitudeMode = mode), imported.applyTo(current))
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
        }
    }
}
