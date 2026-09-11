package voidmei.telemetry

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LivePollingIntervalTest {
    @Test fun fastestIntervalWaitsForRequestsAndCancelsWithoutStartingAnotherRound() = runTest {
        val starts = mutableListOf<Long>()
        var active = 0
        var peakActive = 0
        val transport = TelemetryTransport { path ->
            active++
            peakActive = maxOf(peakActive, active)
            try {
                if (path == "/state") starts += currentTime
                delay(35)
                if (path == "/state") """{"valid":true,"TAS, km/h":360,"Vy, m/s":2}"""
                else """{"valid":true,"type":"test"}"""
            } finally { active-- }
        }
        val states = mutableListOf<ConnectionState>()
        val job = launch {
            TelemetryPoller(transport, intervalMs = 10, timeSource = testScheduler.timeSource)
                .states().toList(states)
        }
        runCurrent()
        advanceTimeBy(150)
        runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(1000)
        assertEquals(listOf(0L, 45L, 90L, 135L), starts)
        assertEquals(2, peakActive) // One state/indicators pair; no overlapping rounds.
        assertEquals(0, active)
        val flying = states.filterIsInstance<ConnectionState.Flying>()
        assertEquals(3, flying.size)
        assertNull(flying.first().metrics.specificExcessPowerMps)
        flying.drop(1).forEach { assertEquals(2.0, it.metrics.specificExcessPowerMps) }
    }

    @Test fun customIntervalChangesKeepTheSepHistoryOnTheSameClock() = runTest {
        var interval = 100L
        val states = mutableListOf<ConnectionState>()
        val transport = TelemetryTransport { path ->
            if (path == "/state") {
                // Constant +10 m/s energy-height rate, independent of request cadence.
                val speed = kotlin.math.sqrt(10000.0 + 2 * FlightCalculator.G * 10 * currentTime / 1000.0) * 3.6
                """{"valid":true,"TAS, km/h":$speed,"Vy, m/s":2}"""
            } else """{"valid":true,"type":"test"}"""
        }
        val job = launch {
            TelemetryPoller(transport, intervalProvider = { interval }, timeSource = testScheduler.timeSource)
                .states().toList(states)
        }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        interval = 80
        advanceTimeBy(100)
        runCurrent()
        advanceTimeBy(800)
        runCurrent()
        job.cancelAndJoin()
        val flying = states.filterIsInstance<ConnectionState.Flying>()
        assertEquals(1, states.count { it == ConnectionState.Connecting })
        assertEquals(22, flying.size)
        assertNull(flying.first().metrics.specificExcessPowerMps)
        flying.drop(1).forEach {
            assertEquals(12.0, assertNotNull(it.metrics.specificExcessPowerMps), 1e-8)
        }
    }

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
        for (invalid in listOf(0L, 9L, 5001L, Long.MAX_VALUE)) {
            var requests = 0
            val transport = TelemetryTransport { requests++; """{"valid":true}""" }
            assertFailsWith<IllegalArgumentException> {
                TelemetryPoller(transport, intervalProvider = { invalid }).states().collect()
            }
            assertEquals(2, requests)
        }
    }
}
