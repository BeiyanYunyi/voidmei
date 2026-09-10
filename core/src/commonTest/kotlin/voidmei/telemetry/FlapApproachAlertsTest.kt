package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class FlapApproachAlertsTest {
    private val model = FlapLimits(listOf(FlapLimitPoint(.25, 600.0), FlapLimitPoint(.5, 500.0), FlapLimitPoint(1.0, 300.0)))
    private fun state(flaps: Double?, speed: Double = 500.0, aircraft: String = "test") = ConnectionState.Flying(
        TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!.copy(
            aircraft = aircraft, flapsPercent = flaps, iasKmh = speed), FlightMetrics())
    private fun update(evaluator: FlightAlerts, flaps: Double?, time: Long, speed: Double = 500.0, aircraft: String = "test") =
        evaluator.update(state(flaps, speed, aircraft), null, time, false, flapLimitKmh = model.speedAt(flaps), flapModel = model).active

    @Test fun extensionUsesEightPointsAndStableOrRetractingUsesTwo() {
        val evaluator = FlightAlerts()
        assertTrue(update(evaluator, 40.0, 0).isEmpty())
        assertTrue(update(evaluator, 42.0, 100).isEmpty()) // Exactly eight points remains below strict threshold.
        assertEquals(listOf(FlightAlert.FLAP_LIMIT), update(evaluator, 43.0, 200))
        assertTrue(update(evaluator, 43.0, 300).isEmpty()) // No fictional one-second extension hold.
        assertTrue(update(evaluator, 42.0, 400).isEmpty())
        assertTrue(update(FlightAlerts(), 48.0, 0).isEmpty())
        assertEquals(listOf(FlightAlert.FLAP_LIMIT), update(FlightAlerts(), 49.0, 0))
    }

    @Test fun missingDataDisconnectDuplicateTimeAndAircraftChangeDropMotionHistory() {
        val evaluator = FlightAlerts()
        update(evaluator, 40.0, 0)
        update(evaluator, null, 100)
        assertTrue(update(evaluator, 43.0, 200).isEmpty())
        update(evaluator, 40.0, 300)
        assertTrue(update(evaluator, 43.0, 300).isEmpty())
        assertTrue(update(evaluator, 44.0, 400).isEmpty())
        update(evaluator, 40.0, 500)
        assertTrue(update(evaluator, 43.0, 3501).isEmpty())
        update(evaluator, 40.0, 3600)
        assertTrue(update(evaluator, 43.0, 3700, aircraft = "other").isEmpty())
        evaluator.update(ConnectionState.WaitingForFlight, null, 3800, false)
        assertTrue(update(evaluator, 44.0, 3900, aircraft = "other").isEmpty())
    }

    @Test fun fullFlapsAtLowSpeedDoNotWarnFromSaturatedTablePosition() {
        assertTrue(update(FlightAlerts(), 100.0, 0, 200.0).isEmpty())
        assertEquals(listOf(FlightAlert.FLAP_LIMIT), update(FlightAlerts(), 100.0, 0, 300.0))
        assertTrue(update(FlightAlerts(), 0.0, 0, 700.0).isEmpty())
        assertTrue(update(FlightAlerts(), null, 0, 700.0).isEmpty())
    }
}
