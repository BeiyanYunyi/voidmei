package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class PositiveAoaMarginTest {
    @Test fun variableWingMarginInterpolatesBothSweepAndFlaps() {
        val flight = TelemetryParser.parse("""{"valid":true,"AoA, deg":5,"flaps, %":50}""",
            """{"valid":true,"type":"test","wing_sweep_indicator":0.5}""")!!
        val forward = WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)
        val swept = forward.copy(sweep = 1.0, cleanMaxAoA = 30.0, flapsMaxAoA = 20.0)
        val model = AircraftAlertModel("test", FlightModelParameters(null, null, listOf(forward, swept), true, emptyList()))
        assertEquals(PositiveAoaMargin(15.0, 0.75), PositiveAoaMargin.fromTelemetry(flight, model))
        assertEquals(PositiveAoaMargin(10.0, 2.0 / 3.0), PositiveAoaMargin.fromTelemetry(flight.copy(wingSweepRatio = 0.0), model))
        assertEquals(PositiveAoaMargin(20.0, 0.8), PositiveAoaMargin.fromTelemetry(flight.copy(wingSweepRatio = 1.0), model))
        for (sweep in listOf(null, -0.1, 1.1, Double.NaN))
            assertNull(PositiveAoaMargin.fromTelemetry(flight.copy(wingSweepRatio = sweep), model))
    }

    @Test fun marginUsesCurrentConfigurationAndRejectsUnknownInputs() {
        val flight = TelemetryParser.parse("""{"valid":true,"AoA, deg":5,"flaps, %":0}""",
            """{"valid":true,"type":"test"}""")!!
        val wing = WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)
        val parameters = FlightModelParameters(null, null, listOf(wing), false, emptyList())
        val model = AircraftAlertModel("test", parameters)
        assertEquals(PositiveAoaMargin(15.0, 0.75), PositiveAoaMargin.fromTelemetry(flight, model))
        assertEquals(PositiveAoaMargin(5.0, 0.5), PositiveAoaMargin.fromTelemetry(flight.copy(flapsPercent = 100.0), model))
        assertEquals(PositiveAoaMargin(-5.0, -0.25), PositiveAoaMargin.fromTelemetry(flight.copy(angleOfAttackDeg = 25.0), model))
        assertEquals(PositiveAoaMargin(25.0, 1.25), PositiveAoaMargin.fromTelemetry(flight.copy(angleOfAttackDeg = -5.0), model))
        assertNull(PositiveAoaMargin.fromTelemetry(flight, null))
        assertNull(PositiveAoaMargin.fromTelemetry(flight.copy(aircraft = "other"), model))
        for (angle in listOf(null, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(PositiveAoaMargin.fromTelemetry(flight.copy(angleOfAttackDeg = angle), model))
        assertNull(PositiveAoaMargin.fromTelemetry(flight.copy(flapsPercent = null), model))
        for (limit in listOf(null, 0.0, -5.0, Double.NaN))
            assertNull(PositiveAoaMargin.fromTelemetry(flight, AircraftAlertModel("test", parameters.copy(wings = listOf(wing.copy(cleanMaxAoA = limit))))))
        assertNull(PositiveAoaMargin.fromTelemetry(flight, AircraftAlertModel("test", parameters.copy(variableSweep = true))))
    }
}
