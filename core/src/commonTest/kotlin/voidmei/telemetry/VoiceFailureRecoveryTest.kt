package voidmei.telemetry

import kotlin.test.*
import voidmei.fm.ControlEffectiveSpeeds

class VoiceFailureRecoveryTest {
    private val speeds = ControlEffectiveSpeeds(400.0)
    private fun flight(speed: Double = 500.0) = ConnectionState.Flying(TelemetryParser.parse(
        """{"valid":true,"IAS, km/h":$speed}""", """{"valid":true,"type":"test"}""")!!, FlightMetrics())
    private fun FlightAlerts.sample(time: Long, speed: Double = 500.0) =
        update(flight(speed), null, time, true, controlSpeeds = speeds)

    @Test fun failedOneShotCanRetryWithoutWaitingForConditionToClear() {
        val evaluator = FlightAlerts()
        val failed = evaluator.sample(0)
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, failed.voice)
        evaluator.voiceFailed(assertNotNull(failed.voiceAttemptId))
        assertNull(evaluator.sample(100).voice)
        assertNull(evaluator.sample(1999).voice)
        val retried = evaluator.sample(2000)
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, retried.voice)
        assertNotEquals(failed.voiceAttemptId, retried.voiceAttemptId)
        evaluator.voiceFailed(failed.voiceAttemptId!!) // A late duplicate must not undo the retry.
        assertNull(evaluator.sample(12000).voice)
    }

    @Test fun failureDoesNotQueueAConditionWhichHasDisappeared() {
        val evaluator = FlightAlerts()
        val failed = evaluator.sample(0)
        evaluator.voiceFailed(failed.voiceAttemptId!!)
        assertTrue(evaluator.sample(2000, 300.0).active.isEmpty())
        assertNull(evaluator.sample(4000, 300.0).voice)
        assertEquals(FlightAlert.AILERON_EFFECTIVENESS, evaluator.sample(5000).voice)
    }

    @Test fun resetAndClockRollbackDoNotReuseAttemptIdentifiers() {
        val evaluator = FlightAlerts()
        val old = evaluator.sample(10000)
        evaluator.update(ConnectionState.WaitingForFlight, null, 11000, true)
        val fresh = evaluator.sample(12000)
        assertNotEquals(old.voiceAttemptId, fresh.voiceAttemptId)
        evaluator.voiceFailed(old.voiceAttemptId!!)
        assertNull(evaluator.sample(22000).voice)
        val rollback = evaluator.sample(0)
        assertNotEquals(fresh.voiceAttemptId, rollback.voiceAttemptId)
        evaluator.voiceFailed(fresh.voiceAttemptId!!)
        assertNull(evaluator.sample(10000).voice)
    }

    @Test fun failedFuelVoiceDoesNotSpendItsMinuteCooldown() {
        val evaluator = FlightAlerts()
        val lowFuel = flight().let { it.copy(telemetry = it.telemetry.copy(fuelKg = 5.0, fuelCapacityKg = 100.0)) }
        val first = evaluator.update(lowFuel, null, 0, true)
        assertEquals(FlightAlert.LOW_FUEL, first.voice)
        evaluator.voiceFailed(first.voiceAttemptId!!)
        assertEquals(FlightAlert.LOW_FUEL, evaluator.update(lowFuel, null, 2000, true).voice)
        assertNull(evaluator.update(lowFuel, null, 5000, true).voice)
    }
}
