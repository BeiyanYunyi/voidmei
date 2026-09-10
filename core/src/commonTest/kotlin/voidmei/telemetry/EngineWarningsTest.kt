package voidmei.telemetry

import kotlin.test.*

class EngineWarningsTest {
    private fun engine(index: Int, throttle: Double?, thrust: Double?) = Engine(index, throttle, null, null, thrust, null, null)
    private fun telemetry(load: Double? = -1.0, engines: List<Engine>) = TelemetryParser.parse(
        """{"valid":true}""", """{"valid":true,"type":"test"}""")!!.copy(loadG = load, engines = engines)

    @Test fun usesThrottleAndThrustOfTheSameEngine() {
        val t = telemetry(engines = listOf(engine(1, 100.0, 500.0), engine(2, 0.0, 0.0)))
        assertTrue(EngineWarnings.lowThrustUnderNegativeLoad(t).isEmpty())
        assertEquals(setOf(2), EngineWarnings.lowThrustUnderNegativeLoad(t.copy(engines = listOf(
            engine(1, 100.0, 500.0), engine(2, 100.0, 49.99)))))
    }

    @Test fun requiresStrictThresholdsAndValidKnownValues() {
        for ((throttle, thrust) in listOf(50.0 to 0.0, 100.0 to 50.0, null to 0.0,
            100.0 to null, Double.NaN to 0.0, 100.0 to Double.NaN, 100.0 to -1.0)) {
            assertTrue(EngineWarnings.lowThrustUnderNegativeLoad(telemetry(engines = listOf(engine(1, throttle, thrust)))).isEmpty())
        }
        for (load in listOf(null, 0.0, 1.0, Double.NaN)) {
            assertTrue(EngineWarnings.lowThrustUnderNegativeLoad(telemetry(load, listOf(engine(1, 100.0, 0.0)))).isEmpty())
        }
        assertEquals(setOf(1), EngineWarnings.lowThrustUnderNegativeLoad(telemetry(-.01, listOf(engine(1, 50.01, 0.0)))))
        assertTrue(EngineWarnings.lowThrustUnderNegativeLoad(telemetry(engines = listOf(engine(1, 100.0, 0.0), engine(1, 100.0, 0.0)))).isEmpty())
    }

    @Test fun integratedAlertUsesFiveSecondCooldownAndRemainsVisibleWhenMuted() {
        val state = ConnectionState.Flying(telemetry(engines = listOf(engine(2, 100.0, 0.0))), FlightMetrics())
        val evaluator = FlightAlerts()
        val alert = FlightAlert.NEGATIVE_LOAD_LOW_THRUST
        val muted = evaluator.update(state, null, 0, true, setOf("fail_engine"))
        assertEquals(listOf(alert), muted.active)
        assertNull(muted.voice)
        assertEquals(alert, evaluator.update(state, null, 100, true).voice)
        for (time in listOf(1100L, 2100L, 3100L, 4100L)) assertNull(evaluator.update(state, null, time, true).voice)
        assertEquals(alert, evaluator.update(state, null, 5100, true).voice)
        val recovered = state.copy(telemetry = state.telemetry.copy(loadG = 1.0))
        assertTrue(evaluator.update(recovered, null, 5200, true).active.isEmpty())
    }
}
