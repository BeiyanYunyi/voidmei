package voidmei.telemetry

import kotlin.test.*

class ControlSurfacePercentTest {
    @Test fun allControlReadingsPreserveSignedRangeAndRejectUnavailableValues() {
        val t = TelemetryParser.parse("""{"valid":true,"aileron, %":0,"elevator, %":67,"rudder, %":-32}""",
            """{"valid":true}""")!!
        val fields = listOf(HudField.AILERON, HudField.ELEVATOR, HudField.RUDDER)
        val original = ConnectionState.Flying(t, FlightMetrics())
        assertEquals(listOf(0.0, 67.0, -32.0), fields.map { it.value(original) })
        for (value in listOf(null, -100.1, 100.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            val flight = original.copy(telemetry = t.copy(aileronPercent = value, elevatorPercent = value, rudderPercent = value))
            assertNull(controlSurfacePercent(value))
            fields.forEach { assertNull(it.value(flight)) }
        }
        for (value in listOf(-100.0, -32.0, 0.0, 67.0, 100.0)) {
            val flight = original.copy(telemetry = t.copy(aileronPercent = value, elevatorPercent = value, rudderPercent = value))
            assertEquals(value, controlSurfacePercent(value))
            fields.forEach { assertEquals(value, it.value(flight)) }
        }
    }
}
