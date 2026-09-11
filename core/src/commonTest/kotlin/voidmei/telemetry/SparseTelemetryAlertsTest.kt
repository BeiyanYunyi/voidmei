package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.*

class SparseTelemetryAlertsTest {
    private fun flight(fuel: Double? = null, flaps: Double? = null, radio: Double? = null) = ConnectionState.Flying(
        TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", fuelKg = fuel, fuelCapacityKg = 100.0,
                iasKmh = 500.0, gearPercent = 0.0, flapsPercent = flaps, radioAltitudeRaw = radio), FlightMetrics())

    @Test fun fiveSecondPollsRespectFuelCooldownWithoutLosingTheVisual() {
        val evaluator = FlightAlerts()
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(flight(5.0), null, 0, true).voice)
        for (time in 5000L..55000L step 5000) {
            val result = evaluator.update(flight(5.0), null, time, true)
            assertEquals(listOf(FlightAlert.LOW_FUEL), result.active)
            assertNull(result.voice)
        }
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(flight(5.0), null, 60000, true).voice)
    }

    @Test fun slowPollsKeepSpokenControlEpisodeAndQueueOtherActiveControls() {
        val evaluator = FlightAlerts()
        val speeds = ControlEffectiveSpeeds(400.0, 400.0, 400.0)
        fun update(time: Long, speed: Double = 500.0) = evaluator.update(
            flight().let { it.copy(telemetry = it.telemetry.copy(iasKmh = speed)) }, null, time, true, controlSpeeds = speeds)
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, update(0).voice)
        assertEquals(FlightAlert.ELEVATOR_EFFECTIVENESS, update(5000).voice)
        assertEquals(FlightAlert.RUDDER_EFFECTIVENESS, update(10000).voice)
        for (time in 15000L..40000L step 5000) {
            val result = update(time)
            assertEquals(3, result.active.size)
            assertNull(result.voice)
        }
        assertTrue(update(45000, 300.0).active.isEmpty())
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, update(50000).voice)
    }

    @Test fun sparseSamplesStillDiscardMotionButKeepUnrelatedVoiceHistory() {
        val evaluator = FlightAlerts()
        val flaps = FlapLimits(listOf(FlapLimitPoint(.5, 500.0), FlapLimitPoint(1.0, 300.0)))
        fun update(time: Long, percent: Double, radio: Double) = evaluator.update(
            flight(5.0, percent, radio), null, time, true, flapModel = flaps)
        assertEquals(FlightAlert.LOW_FUEL, update(0, 40.0, 100.0).voice)
        val sparse = update(5000, 43.0, 1.0)
        assertEquals(listOf(FlightAlert.LOW_FUEL), sparse.active)
        assertNull(sparse.voice)
        // A subsequent close sample can establish extension again.
        assertTrue(FlightAlert.FLAP_LIMIT in update(5100, 44.0, 1.0).active)
    }

    @Test fun actualSessionChangesAndClockRollbackStillResetCooldowns() {
        for (change in listOf("disconnect", "aircraft", "clock")) {
            val evaluator = FlightAlerts()
            evaluator.update(flight(5.0), null, 10000, true)
            evaluator.update(ConnectionState.Delayed, null, 10500, true)
            val state = if (change == "aircraft") flight(5.0).let {
                it.copy(telemetry = it.telemetry.copy(aircraft = "other"))
            } else flight(5.0)
            if (change == "disconnect") evaluator.update(ConnectionState.Disconnected("test"), null, 11000, true)
            val result = evaluator.update(state, null, if (change == "clock") 9000 else 12000, true)
            assertEquals(FlightAlert.LOW_FUEL, result.voice, change)
        }
    }

    @Test fun delayedSamplesClearVisualsAndMotionWithoutClearingFuelCooldown() {
        val evaluator = FlightAlerts()
        val flaps = FlapLimits(listOf(FlapLimitPoint(.5, 500.0), FlapLimitPoint(1.0, 300.0)))
        fun update(time: Long, percent: Double, radio: Double) = evaluator.update(
            flight(5.0, percent, radio), null, time, true, flapModel = flaps)
        assertEquals(FlightAlert.LOW_FUEL, update(0, 40.0, 100.0).voice)
        assertEquals(AlertUpdate(emptyList(), null), evaluator.update(ConnectionState.Delayed, null, 1000, true))
        val resumed = update(1500, 43.0, 1.0)
        assertEquals(listOf(FlightAlert.LOW_FUEL), resumed.active)
        assertNull(resumed.voice)
        assertNull(update(3000, 43.0, 1.0).voice)
        assertEquals(FlightAlert.LOW_FUEL, update(60000, 43.0, 1.0).voice)
    }

    @Test fun delayDoesNotRearmAnAlreadySpokenControlEpisode() {
        val evaluator = FlightAlerts()
        fun update(time: Long) = evaluator.update(flight(), null, time, true,
            controlSpeeds = ControlEffectiveSpeeds(400.0, null, null))
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, update(0).voice)
        evaluator.update(ConnectionState.Delayed, null, 1000, true)
        val resumed = update(30000)
        assertEquals(listOf(FlightAlert.AILERON_EFFECTIVENESS), resumed.active)
        assertNull(resumed.voice)
    }
}
