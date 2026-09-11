package voidmei.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import voidmei.telemetry.*
import voidmei.recording.PerformanceObservation
import java.nio.file.Files
import java.io.BufferedWriter
import java.io.StringWriter
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*

class PerformanceRecordingTest {
    private val t = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"first"}""")!!
    private fun flight(name: String = "first", alt: Double) = ConnectionState.Flying(t.copy(aircraft = name, altitudeM = alt), FlightMetrics())

    @Test fun onlyCommittedSamplesProduceObservationsAndEachAircraftStartsFresh() = runBlocking {
        val root = Files.createTempDirectory("voidmei-performance-record")
        val recorder = FlightRecorder(this)
        val observations = ConcurrentLinkedQueue<PerformanceObservation>()
        val collector = launch(Dispatchers.Unconfined) { recorder.performance.collect { observations += it } }
        try {
            recorder.start(root)
            recorder.record(flight(alt = 150.0), 1000, 0)
            recorder.record(flight(alt = 200.0), 6000, 5000)
            recorder.record(flight("second", 500.0), 7000, 6000)
            recorder.record(flight("second", 600.0), 12000, 11000)
            recorder.stop()
            assertEquals(listOf(PerformanceObservation.Climb(200, 5.0, 10.0), PerformanceObservation.Climb(600, 5.0, 20.0)), observations.toList())
            assertTrue(performanceMessage(observations.first()).contains("10.0 m/s"))
            val saved = assertNotNull(recorder.lastRecording.value)
            assertEquals(3, Files.readAllLines(saved.flight).size)
        } finally { collector.cancelAndJoin(); recorder.close(); root.toFile().deleteRecursively() }
    }

    @Test fun flushFailureCannotReportTheUnwrittenRollObservation() = runBlocking {
        val root = Files.createTempDirectory("voidmei-performance-failure")
        val recorder = FlightRecorder(this, openWriter = {
            object : BufferedWriter(StringWriter()) { override fun flush() { throw IOException("disk failure") } }
        })
        val observations = ConcurrentLinkedQueue<PerformanceObservation>()
        val collector = launch(Dispatchers.Unconfined) { recorder.performance.collect { observations += it } }
        try {
            recorder.start(root)
            recorder.record(ConnectionState.Flying(t.copy(iasKmh = 200.0, rollRateDegPerSecond = 100.0, aileronPercent = 80.0), FlightMetrics()), 1000, 0)
            recorder.stop()
            assertIs<RecordingState.Failed>(recorder.state.value)
            assertTrue(observations.isEmpty())
        } finally { collector.cancelAndJoin(); recorder.close(); root.toFile().deleteRecursively() }
    }
}
