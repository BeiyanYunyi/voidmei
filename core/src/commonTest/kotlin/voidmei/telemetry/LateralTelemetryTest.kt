package voidmei.telemetry

import kotlin.test.*
import voidmei.recording.*
import voidmei.config.*

class LateralTelemetryTest {
    private fun sample(value: String) = TelemetryParser.parse(
        """{"valid":true,"AoS, deg":$value,"Wx, deg/s":$value,"AoA, deg":10,"Wx":99}""",
        """{"valid":true,"aviahorizon_roll":45}""")!!

    @Test fun exactKeysPreserveSignAndDoNotUseAttitudeOrAttackAngle() {
        val flight = ConnectionState.Flying(sample("-12.5"), FlightMetrics())
        assertEquals(-12.5, flight.telemetry.sideslipAngleDeg)
        assertEquals(-12.5, flight.telemetry.rollRateDegPerSecond)
        assertEquals(-12.5, HudField.SIDESLIP.value(flight))
        assertEquals(-12.5, HudField.ROLL_RATE.value(flight))
        for (invalid in listOf("null", "true", "\"12\"", "1e999", "-65535")) {
            val telemetry = sample(invalid)
            assertNull(telemetry.sideslipAngleDeg)
            assertNull(telemetry.rollRateDegPerSecond)
        }
        val settings = AppSettings(hudFields = listOf("roll_rate", "sideslip"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(listOf(HudField.ROLL_RATE, HudField.SIDESLIP), HudField.selected(settings.hudFields))
    }

    @Test fun recordingAndReplayPreserveSignedValuesAndUnknowns() {
        val header = FlightCsv.flightHeader.split(',')
        assertEquals(listOf("fuel_pressure_raw", "sideslip_deg", "roll_rate_degps"), header.subList(34, 37))
        val text = FlightCsv.flightHeader + "\n" + listOf("-12.5", "null", "20").mapIndexed { i, value ->
            FlightCsv.flightRow(i.toLong(), 1000L + i * 1000, i * 1000L, ConnectionState.Flying(sample(value), FlightMetrics()))
        }.joinToString("\n")
        val analysis = FlightRecordPlots.analyze(text)
        for (field in listOf("sideslip_deg", "roll_rate_degps")) {
            assertEquals(RecordedRange(2, -12.5, 20.0), analysis.summary.ranges[field])
            assertTrue(analysis.plots.getValue(field).none { it.connectFromPrevious })
            val replay = RecordedReplay(text)
            assertEquals(-12.5, replay.frame(0).values[field])
            assertNull(replay.frame(1).values[field])
        }
        val old = text.lines().joinToString("\n") { it.split(',').take(35).joinToString(",") }
        assertNull(FlightRecordPlots.analyze(old).plots["roll_rate_degps"])
    }
}
