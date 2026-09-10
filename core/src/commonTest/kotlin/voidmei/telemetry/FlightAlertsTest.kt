package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.WingLimits

class FlightAlertsTest {
    private fun flight(fuel: Double = 5.0) = ConnectionState.Flying(TelemetryParser.parse(
        """{"valid":true,"Mfuel, kg":$fuel,"Mfuel0, kg":100,"IAS, km/h":780,"M":1.95,"AoA, deg":19.1}""",
        """{"valid":true,"type":"test"}""")!!, FlightMetrics())

    @Test fun disabledHigherPriorityVoiceKeepsVisualAndAllowsNextWarning() {
        val evaluator = FlightAlerts()
        val limits = WingLimits(800.0, 2.0, -10.0, 20.0)
        val update = evaluator.update(flight(0.0), limits, 0, true, setOf("fail_nofuel"))
        assertEquals(FlightAlert.EMPTY_FUEL, update.active.first())
        assertEquals(FlightAlert.CRITICAL_AOA, update.voice)
        // Disabled fuel warning did not consume its per-alert cooldown.
        assertEquals(FlightAlert.EMPTY_FUEL, evaluator.update(flight(0.0), limits, 2000, true).voice)
    }

    @Test fun disablingAllVoicesDoesNotConsumeGlobalCooldown() {
        val evaluator = FlightAlerts()
        assertNull(evaluator.update(flight(), null, 0, true, setOf("warn_lowfuel")).voice)
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(flight(), null, 100, true).voice)
    }

    @Test fun mechanizationWarningsMatchLegacyThresholdsWithoutInventingMissingValues() {
        fun active(gear: Double?, brake: Double?, descent: Double?, speed: Double? = 450.0, limit: Double? = null): List<FlightAlert> {
            val state = flight(50.0).let { it.copy(telemetry = it.telemetry.copy(
                gearPercent = gear, airbrakePercent = brake, verticalSpeedMps = descent, iasKmh = speed)) }
            return FlightAlerts().update(state, null, 0, false, gearLimitKmh = limit).active
        }
        assertEquals(listOf(FlightAlert.GEAR_LIMIT, FlightAlert.HIGH_DESCENT, FlightAlert.AIRBRAKE_EXTENDED),
            active(50.0, 90.0, -8.0, limit = 450.0))
        assertEquals(listOf(FlightAlert.HIGH_DESCENT), active(100.0, 100.0, -8.0))
        assertEquals(listOf(FlightAlert.AIRBRAKE_EXTENDED), active(0.0, 90.0, -8.0, limit = 450.0))
        assertTrue(active(49.9, 89.9, -7.99, 449.99, 450.0).isEmpty())
        for (bad in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0)) {
            assertTrue(active(bad, 100.0, -10.0, limit = 450.0).isEmpty())
        }
        assertTrue(active(100.0, 0.0, null, limit = null).isEmpty())
        assertTrue(active(100.0, 0.0, Double.NaN, speed = null, limit = 450.0).isEmpty())
        assertTrue(active(1.0, 0.0, 0.0, limit = Double.NaN).isEmpty())
    }

    @Test fun flapWarningRequiresExtendedFlapsAndAnActualModelLimit() {
        val state = flight(50.0).let { it.copy(telemetry = it.telemetry.copy(flapsPercent = 50.0, iasKmh = 400.0)) }
        assertEquals(listOf(FlightAlert.FLAP_LIMIT), FlightAlerts().update(state, null, 0, true, flapLimitKmh = 400.0).active)
        assertTrue(FlightAlerts().update(state, null, 0, true).active.isEmpty())
        for (flaps in listOf(null, 0.0, -1.0, 101.0, Double.NaN)) {
            assertTrue(FlightAlerts().update(state.copy(telemetry = state.telemetry.copy(flapsPercent = flaps)), null, 0, true, flapLimitKmh = 400.0).active.isEmpty())
        }
        assertTrue(FlightAlerts().update(state, null, 0, true, flapLimitKmh = 400.01).active.isEmpty())
    }

    @Test fun absentModelOnlyAllowsFuelWarnings() {
        val alerts = FlightAlerts().update(flight(), null, 0, true)
        assertEquals(listOf(FlightAlert.LOW_FUEL), alerts.active)
        assertEquals(FlightAlert.LOW_FUEL, alerts.voice)
    }
    @Test fun priorityAndCooldownDoNotFloodIdenticalSamples() {
        val evaluator = FlightAlerts()
        val limits = WingLimits(800.0, 2.0, -10.0, 20.0)
        assertEquals(FlightAlert.EMPTY_FUEL, evaluator.update(flight(0.0), limits, 0, true).voice)
        assertNull(evaluator.update(flight(0.0), limits, 100, true).voice)
        assertEquals(FlightAlert.CRITICAL_AOA, evaluator.update(flight(0.0), limits, 2000, true).voice)
        val lowerAoA = flight(0.0).let { it.copy(telemetry = it.telemetry.copy(angleOfAttackDeg = 0.0)) }
        assertEquals(FlightAlert.IAS_LIMIT, evaluator.update(lowerAoA, limits, 4000, true).voice)
        assertEquals(FlightAlert.MACH_LIMIT, evaluator.update(lowerAoA, limits, 6000, true).voice)
        assertNull(evaluator.update(lowerAoA, limits, 8000, true).voice)
    }
    @Test fun disablingVoiceDoesNotSuppressVisualWarningsOrConsumeCooldown() {
        val evaluator = FlightAlerts()
        val update = evaluator.update(flight(), null, 0, false)
        assertEquals(listOf(FlightAlert.LOW_FUEL), update.active)
        assertNull(update.voice)
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(flight(), null, 100, true).voice)
    }
    @Test fun disconnectAndAircraftSwitchResetHistory() {
        val evaluator = FlightAlerts()
        evaluator.update(flight(), null, 0, true)
        assertTrue(evaluator.update(ConnectionState.WaitingForFlight, null, 100, true).active.isEmpty())
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(flight(), null, 200, true).voice)
        val other = flight().let { it.copy(telemetry = it.telemetry.copy(aircraft = "other")) }
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(other, null, 300, true).voice)
    }
    @Test fun unknownFuelAndInvalidLimitsAreNotWarnings() {
        val unknown = flight().let { it.copy(telemetry = it.telemetry.copy(fuelKg = null)) }
        assertTrue(FlightAlerts().update(unknown, WingLimits(0.0, Double.NaN, 10.0, -1.0), 0, true).active.isEmpty())
    }
}
