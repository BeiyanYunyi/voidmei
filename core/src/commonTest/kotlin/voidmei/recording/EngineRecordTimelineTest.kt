package voidmei.recording

import kotlin.test.*

class EngineRecordTimelineTest {
    private val flight = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n" +
        "0,2000,0,test,300\n1,1000,100,test,310\n2,3000,200,test,320\n"
    private val engines = "sample_id,utc_epoch_ms,engine_index,rpm\n" +
        "0,2000,1,1000\n0,2000,2,2000\n1,1000,2,2200\n2,3000,1,1500\n2,3000,2,2500\n"

    @Test fun engineGapsBreakLinesAndUtcRollbackDoesNotChangeTimeAxis() {
        val first = EngineRecordTimeline.analyze(flight, engines, 1)
        assertEquals("test", first.summary.aircraft)
        assertEquals(200, first.summary.spanMs)
        assertEquals(RecordedRange(2, 1000.0, 1500.0), first.summary.ranges["rpm"])
        assertEquals(listOf(0L, 200L), first.plots.getValue("rpm").map { it.elapsedMs })
        assertTrue(first.plots.getValue("rpm").none { it.connectFromPrevious })
        val second = EngineRecordTimeline.analyze(flight, engines, 2)
        assertEquals(listOf(0L, 100L, 200L), second.plots.getValue("rpm").map { it.elapsedMs })
        assertTrue(second.plots.getValue("rpm").drop(1).all { it.connectFromPrevious })
    }

    @Test fun joinedSnapshotCanBeWindowedWithoutLosingMissingEngineFrames() {
        val loaded = EngineRecordTimeline.read(flight, engines, 1)
        val window = FlightRecordWindow.analyze(loaded.text, 100, 200, listOf("rpm"))
        assertEquals(100, window.firstPointOffsetMs)
        assertEquals(2, window.analysis.summary.samples)
        assertEquals(RecordedRange(1, 1500.0, 1500.0), window.analysis.summary.ranges["rpm"])
        assertEquals(listOf(RecordedPlotPoint(100, 1500.0, false, sampleId = 2)), window.analysis.plots["rpm"])
        val missing = FlightRecordWindow.analyze(loaded.text, 100, 100, listOf("rpm"))
        assertTrue(missing.analysis.plots.getValue("rpm").isEmpty())
        assertNull(missing.analysis.summary.ranges["rpm"])
        assertEquals(loaded.analysis, EngineRecordTimeline.analyze(flight, engines, 1))
    }

    @Test fun rejectsWrongFilesAndUnmatchedEngineSamples() {
        assertFails { EngineRecordTimeline.analyze(flight, engines.replace("1,1000,2", "1,1001,2"), 1) }
        assertFails { EngineRecordTimeline.analyze(flight, engines + "3,4000,1,1600\n", 1) }
        assertFails { EngineRecordTimeline.analyze(flight.replace("1,1000,100,test,310\n", ""), engines, 1) }
        assertFails { EngineRecordTimeline.analyze(flight, engines, 3) }
    }

    @Test fun quotedAircraftNamesSurviveInternalJoin() {
        val quoted = flight.replace("test", "\"test, \"\"plane\"\"\"")
        assertEquals("test, \"plane\"", EngineRecordTimeline.analyze(quoted, engines, 1).summary.aircraft)
    }
}
