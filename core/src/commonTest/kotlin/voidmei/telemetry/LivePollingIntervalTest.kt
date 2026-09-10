package voidmei.telemetry

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LivePollingIntervalTest {
    @Test fun intervalChangesPreserveOneContinuousConnectionAndCancelCleanly() = runTest {
        var interval = 100L
        val samples = mutableListOf<Long>()
        val states = mutableListOf<ConnectionState>()
        val transport = TelemetryTransport { path ->
            if (path == "/state") {
                samples += currentTime
                """{"valid":true,"IAS, km/h":300}"""
            } else """{"valid":true,"type":"test"}"""
        }
        val job = launch { TelemetryPoller(transport, intervalProvider = { interval }).states().toList(states) }
        runCurrent()
        assertEquals(listOf(0L), samples)
        interval = 500
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(0L, 100L), samples)
        advanceTimeBy(499)
        assertEquals(2, samples.size)
        advanceTimeBy(1)
        runCurrent()
        interval = 20
        advanceTimeBy(500)
        runCurrent()
        advanceTimeBy(20)
        runCurrent()
        assertEquals(listOf(0L, 100L, 600L, 1100L, 1120L), samples)
        assertEquals(1, states.count { it == ConnectionState.Connecting })
        assertEquals(5, states.count { it is ConnectionState.Flying })
        job.cancelAndJoin()
        advanceTimeBy(10000)
        assertEquals(5, samples.size)
    }

    @Test fun invalidLiveIntervalsFailInsteadOfCreatingATightPollingLoop() = runTest {
        for (invalid in listOf(0L, 19L, 5001L, Long.MAX_VALUE)) {
            var requests = 0
            val transport = TelemetryTransport { requests++; """{"valid":true}""" }
            assertFailsWith<IllegalArgumentException> {
                TelemetryPoller(transport, intervalProvider = { invalid }).states().collect()
            }
            assertEquals(2, requests)
        }
    }
}
