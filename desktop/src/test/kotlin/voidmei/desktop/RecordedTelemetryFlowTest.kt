package voidmei.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import voidmei.telemetry.ConnectionState

class RecordedTelemetryFlowTest {
    @Test fun transientWarningIsEvaluatedWhileDisplayIsBlocked(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val processed = CompletableDeferred<Unit>()
        val evaluator = voidmei.telemetry.FlightAlerts()
        val base = voidmei.telemetry.TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"type":"test"}""")!!
        fun flight(aoa: Double) = ConnectionState.Flying(base.copy(iasKmh = 300.0, angleOfAttackDeg = aoa),
            voidmei.telemetry.FlightMetrics())
        val voices = mutableListOf<voidmei.telemetry.FlightAlert>()
        val displayed = mutableListOf<List<voidmei.telemetry.FlightAlert>>()
        var time = 0L
        val job = launch {
            flow<ConnectionState> {
                emit(flight(0.0))
                entered.await()
                emit(flight(20.0))
                emit(flight(0.0))
                processed.complete(Unit)
            }.processBeforeDisplay { state ->
                evaluator.update(state, voidmei.fm.WingLimits(null, null, null, 20.0), time, true)
                    .also { update -> update.voice?.let { voices += it }; time += 2000 }
            }.collect { update ->
                displayed += update.active
                if (displayed.size == 1) { entered.complete(Unit); release.await() }
            }
        }
        try {
            withTimeout(5000) { processed.await() }
            assertEquals(listOf(voidmei.telemetry.FlightAlert.CRITICAL_AOA), voices)
            assertEquals(listOf(emptyList()), displayed)
            release.complete(Unit)
            withTimeout(5000) { job.join() }
            assertEquals(listOf(emptyList(), emptyList()), displayed)
        } finally { job.cancelAndJoin() }
    }

    @Test fun blockedDisplayDoesNotBlockOrDropRecording(): Unit = runBlocking {
        val displayEntered = CompletableDeferred<Unit>()
        val releaseDisplay = CompletableDeferred<Unit>()
        val recordedAll = CompletableDeferred<Unit>()
        val recorded = mutableListOf<String>()
        val displayed = mutableListOf<String>()
        val job = launch {
            flow<ConnectionState> {
                emit(ConnectionState.Disconnected("0"))
                displayEntered.await()
                repeat(100) { emit(ConnectionState.Disconnected((it + 1).toString())) }
                recordedAll.complete(Unit)
            }.processBeforeDisplay { recorded += (it as ConnectionState.Disconnected).reason; it }
                .collect { state ->
                    displayed += (state as ConnectionState.Disconnected).reason
                    if (displayed.size == 1) { displayEntered.complete(Unit); releaseDisplay.await() }
                }
        }
        try {
            withTimeout(5000) { recordedAll.await() }
            assertEquals((0..100).map(Int::toString), recorded)
            assertEquals(listOf("0"), displayed)
            releaseDisplay.complete(Unit)
            withTimeout(5000) { job.join() }
            assertEquals(listOf("0", "100"), displayed)
        } finally { job.cancelAndJoin() }
    }
}
