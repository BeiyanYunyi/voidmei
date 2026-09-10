package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class AngleOfAttackAlertsTest {
    private val limits = WingLimits(null, null, -10.0, 20.0)
    private fun flight(aoa: Double?, speed: Double? = 300.0) = ConnectionState.Flying(
        TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", angleOfAttackDeg = aoa, iasKmh = speed), FlightMetrics())

    @Test fun positiveThresholdsAndAirspeedGateMatchLegacyBoundaries() {
        fun active(aoa: Double?, speed: Double? = 300.0, model: WingLimits? = limits) =
            FlightAlerts().update(flight(aoa, speed), model, 0, true).active
        assertEquals(emptyList(), active(15.0))
        assertEquals(listOf(FlightAlert.HIGH_AOA), active(15.01))
        assertEquals(listOf(FlightAlert.HIGH_AOA), active(19.0))
        assertEquals(listOf(FlightAlert.CRITICAL_AOA), active(19.01))
        // A 30-degree model has a 29-degree boundary, not 95% (28.5 degrees).
        assertEquals(listOf(FlightAlert.HIGH_AOA), active(28.6, model = limits.copy(maxAngleOfAttackDeg = 30.0)))
        for (speed in listOf(null, -1.0, 0.0, 80.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(active(25.0, speed).isEmpty())
            assertTrue(active(-10.0, speed).isEmpty())
        }
        assertEquals(listOf(FlightAlert.CRITICAL_AOA), active(25.0, 80.01))
        assertEquals(listOf(FlightAlert.CRITICAL_AOA), active(-9.5))
        for (aoa in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) assertTrue(active(aoa).isEmpty())
        assertTrue(active(25.0, model = null).isEmpty())
    }

    @Test fun criticalConditionDelaysLowerVoiceWithoutHidingItsVisual() {
        val evaluator = FlightAlerts()
        assertEquals(FlightAlert.CRITICAL_AOA, evaluator.update(flight(20.0), limits, 0, true).voice)
        // Refresh the suppression even with audio disabled; maintain continuous telemetry.
        assertNull(evaluator.update(flight(20.0), limits, 1000, false).voice)
        for (time in listOf(2000L, 4000L, 6000L, 8000L, 8999L)) {
            val result = evaluator.update(flight(16.0), limits, time, true)
            assertEquals(listOf(FlightAlert.HIGH_AOA), result.active)
            assertNull(result.voice)
        }
        assertEquals(FlightAlert.HIGH_AOA, evaluator.update(flight(16.0), limits, 9000, true).voice)
        evaluator.update(ConnectionState.WaitingForFlight, null, 9100, true)
        assertEquals(FlightAlert.HIGH_AOA, evaluator.update(flight(16.0), limits, 9200, true).voice)
    }

    @Test fun lowerVoiceSwitchAndCooldownAreIndependentOfItsVisual() {
        val evaluator = FlightAlerts()
        val disabled = evaluator.update(flight(16.0), limits, 0, true, setOf("aoaHigh"))
        assertEquals(listOf(FlightAlert.HIGH_AOA), disabled.active)
        assertNull(disabled.voice)
        assertEquals(FlightAlert.HIGH_AOA, evaluator.update(flight(16.0), limits, 100, true).voice)
        for (time in listOf(2100L, 4100L, 6100L, 8099L))
            assertNull(evaluator.update(flight(16.0), limits, time, true).voice)
        assertEquals(FlightAlert.HIGH_AOA, evaluator.update(flight(16.0), limits, 8100, true).voice)
    }

    @Test fun flapConfigurationAndAircraftBindingDriveBothStages() {
        val model = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("""
            NoFlaps { alphaCritHigh:r=20 }
            FullFlaps { alphaCritHigh:r=12 }
        """)))
        fun update(flaps: Double?, aircraft: String? = "test") = FlightAlerts().updateForAircraft(
            flight(14.0).let { it.copy(telemetry = it.telemetry.copy(flapsPercent = flaps, aircraft = aircraft)) },
            model, 0, true).active
        assertTrue(update(0.0).isEmpty())
        assertEquals(listOf(FlightAlert.HIGH_AOA), update(50.0))
        assertEquals(listOf(FlightAlert.CRITICAL_AOA), update(100.0))
        assertTrue(update(null).isEmpty())
        assertTrue(update(100.0, "other").isEmpty())
        assertTrue(update(100.0, null).isEmpty())
    }
}
