package voidmei.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedTelemetryTest {
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
