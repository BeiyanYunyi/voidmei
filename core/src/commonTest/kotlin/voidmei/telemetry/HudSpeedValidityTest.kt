package voidmei.telemetry

import kotlin.test.*

class HudSpeedValidityTest {
    @Test fun speedFieldsRejectNegativeAndNonFiniteValuesWithoutDiscardingZero() {
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        for (raw in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 1.2, 340.0)) {
            val flight = ConnectionState.Flying(telemetry.copy(iasKmh = raw, tasKmh = raw, mach = raw), FlightMetrics())
            val expected = raw?.takeIf { it.isFinite() && it >= 0 }
            for (field in listOf(HudField.IAS, HudField.TAS, HudField.MACH)) assertEquals(expected, field.value(flight), "$field raw=$raw")
        }
    }
}
