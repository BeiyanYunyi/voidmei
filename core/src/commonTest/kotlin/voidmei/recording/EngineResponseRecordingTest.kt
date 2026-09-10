package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*
import voidmei.fm.EnginePeakKind

class EngineResponseRecordingTest {
    @Test fun monitorOutputSurvivesReplayAndCropWithMissingIntervalsIntact() {
        val t = TelemetryParser.parse("""{"valid":true,"power 1, hp":1000}""", """{"valid":true,"type":"test"}""")!!
        val monitor = EngineResponseMonitor()
        val frames = listOf(0.0, 10.0, 0.0, 0.0, 0.0).mapIndexed { index, percent ->
            val reference = if (index < 3) 1000.0 else 2000.0
            monitor.update(ConnectionState.Flying(t, FlightMetrics(observedEnginePeak = ObservedEnginePeak(
                EnginePeakKind.SHAFT_POWER_HP, percent, reference))), null, index * 1000L) as ConnectionState.Flying
        }
        val csv = FlightCsv.flightHeader + "\n" + frames.mapIndexed { index, flight ->
            FlightCsv.flightRow(index.toLong(), 10000 + index * 1000L, index * 1000L, flight)
        }.joinToString("\n")
        val replay = RecordedReplay(csv)
        val key = "engine_response_percent_per_s"
        frames.forEachIndexed { index, flight ->
            assertEquals(flight.metrics.engineResponsePercentPerSecond, replay.frame(index).values[key])
        }
        assertTrue(replay.frame(1).values.getValue(key)!! > 0)
        assertTrue(replay.frame(2).values.getValue(key)!! < 0)
        assertNull(replay.frame(3).values[key])
        assertEquals(0.0, replay.frame(4).values[key])
        val points = FlightRecordPlots.analyze(csv).plots.getValue(key)
        assertFalse(points.last().connectFromPrevious)
        val crop = RecordedReplay(FlightRecordWindow.analyze(csv, 2000, 4000).text)
        assertTrue(crop.frame(0).values.getValue(key)!! < 0)
        assertNull(crop.frame(1).values[key])
        assertEquals(0.0, crop.frame(2).values[key])
        val old = csv.lines().joinToString("\n") { it.split(',').take(41).joinToString(",") }
        assertNull(RecordedReplay(old).frame(1).values[key])
        assertNull(FlightRecordPlots.analyze(old).plots[key])
    }
}
