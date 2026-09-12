package voidmei.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedTelemetryTest {
    @Test fun establishedPowerAndThrustReferencesSurviveDelayButNotRequestFailure() = runTest {
        for (jet in listOf(false, true)) {
            var round = 0
            val transport = TelemetryTransport { path ->
                if (path == "/state") round++
                if (round == 7) delay(1500)
                if (round == 8) error("connection lost")
                val output = if (round >= 7) 500 else 1000
                val throttle = if (round >= 7) 50 else 100
                if (path == "/state") """{"valid":true,"TAS, km/h":360,"Vy, m/s":2,
                    "throttle 1, %":$throttle,"power 1, hp":${if (jet) 0 else output},
                    "thrust 1, kgs":$output,"magneto 1":${if (jet) -1 else 3}}"""
                else """{"valid":true,"type":"test"}"""
            }
            val states = TelemetryPoller(transport, intervalMs = 1000, timeSource = testScheduler.timeSource)
                .states().take(11).toList()
            assertEquals(100.0, assertIs<ConnectionState.Flying>(states[6]).metrics.observedEnginePeak?.percent)
            assertEquals(ConnectionState.Delayed, states[7])
            val restored = assertIs<ConnectionState.Flying>(states[8])
            assertEquals(50.0, restored.metrics.observedEnginePeak?.percent)
            assertEquals(1000.0, restored.metrics.observedEnginePeak?.reference)
            assertNull(restored.metrics.accelerationMps2)
            assertNull(restored.metrics.specificExcessPowerMps)
            assertIs<ConnectionState.Disconnected>(states[9])
            assertNull(assertIs<ConnectionState.Flying>(states[10]).metrics.observedEnginePeak)
        }
    }

    @Test fun delayedSampleClearsOldDataWithoutOverlappingRequestsAndResetsDerivedHistory() = runTest {
        var round = 0
        var active = 0
        var peak = 0
        val starts = mutableListOf<Long>()
        val transport = TelemetryTransport { path ->
            active++
            peak = maxOf(peak, active)
            try {
                if (path == "/state") { round++; starts += currentTime }
                if (round == 3) delay(1500)
                if (path == "/state") """{"valid":true,"TAS, km/h":360,"Vy, m/s":2}"""
                else """{"valid":true,"type":"test"}"""
            } finally { active-- }
        }
        val states = TelemetryPoller(transport, timeSource = testScheduler.timeSource).states()
            .map { currentTime to it }.take(5).toList()
        assertEquals(listOf(0L, 0L, 100L, 1200L, 1700L), states.map { it.first })
        assertEquals(ConnectionState.Delayed, states[3].second)
        assertEquals(2.0, assertIs<ConnectionState.Flying>(states[2].second).metrics.specificExcessPowerMps)
        assertNull(assertIs<ConnectionState.Flying>(states[4].second).metrics.specificExcessPowerMps)
        assertEquals(listOf(0L, 100L, 200L), starts)
        assertEquals(2, peak)
        assertEquals(0, active)
    }

    @Test fun stoppingAtDelayCancelsTheOriginalPair() = runTest {
        var cancelled = 0
        val transport = TelemetryTransport { try { awaitCancellation() } finally { cancelled++ } }
        val states = TelemetryPoller(transport).states().take(2).toList()
        assertEquals(ConnectionState.Delayed, states.last())
        assertEquals(1000L, currentTime)
        assertEquals(2, cancelled)
    }

    @Test fun validWaitingResponsesAndLongConfiguredIntervalsAreNotRequestDelays() = runTest {
        val transport = TelemetryTransport { """{"valid":false}""" }
        val states = TelemetryPoller(transport, intervalMs = 5000).states().take(3).toList()
        assertEquals(listOf(ConnectionState.Connecting, ConnectionState.WaitingForFlight, ConnectionState.WaitingForFlight), states)
        assertEquals(5000L, currentTime)
    }
}
