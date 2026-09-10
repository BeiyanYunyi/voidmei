package voidmei.recording

import kotlin.test.*

class FlightRecordWindowTest {
    @Test fun selectedCsvPreservesOriginalIdsTimesAndUnplottedColumns() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,note\n" +
            "10,1000,500,test,100,first\n11,2000,1500,test,200,\"带逗号, 和\"\"引号\"\"\"\n"
        val selected = FlightRecordWindow.analyze(csv, 1000, 1000)
        val rows = FlightRecordReader.rows(selected.text).toList()
        assertEquals(listOf("sample_id", "utc_epoch_ms", "elapsed_ms", "aircraft", "ias_kmh", "note"), rows[0])
        assertEquals(listOf("11", "2000", "1500", "test", "200", "带逗号, 和\"引号\""), rows[1])
        assertEquals(2, rows.size)
    }

    private val header = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n"

    @Test fun narrowingWindowRecoversPointsDroppedFromFullPlot() {
        val csv = header + (0..10000).joinToString("\n") { "$it,${1000 + it},${500 + it},test,$it" }
        val full = FlightRecordPlots.analyze(csv)
        assertTrue(full.plots.getValue("ias_kmh").none { it.value == 105.0 })
        val selected = FlightRecordWindow.analyze(csv, 100, 110)
        assertEquals(100, selected.firstPointOffsetMs)
        assertEquals(11, selected.analysis.summary.samples)
        assertEquals(RecordedRange(11, 100.0, 110.0), selected.analysis.summary.ranges["ias_kmh"])
        assertTrue(selected.analysis.plots.getValue("ias_kmh").any { it.value == 105.0 && it.elapsedMs == 5L })
    }

    @Test fun includesBothEndpointsAndDuplicateTimesWithoutInventingEmptyWindows() {
        val csv = header + "0,1000,500,test,100\n1,2000,1000,test,200\n2,2000,1000,test,300\n3,3000,1500,test,400\n"
        val selected = FlightRecordWindow.analyze(csv, 500, 500)
        assertEquals(2, selected.analysis.summary.samples)
        assertEquals(0, selected.analysis.summary.spanMs)
        assertEquals(listOf(200.0, 300.0), selected.analysis.plots.getValue("ias_kmh").map { it.value })
        assertEquals(listOf(1L, 2L), selected.analysis.plots.getValue("ias_kmh").map { it.sampleId })
        assertFails { FlightRecordWindow.analyze(csv, 1, 499) }
        assertFails { FlightRecordWindow.analyze(csv, 500, 499) }
        assertFails { FlightRecordWindow.analyze(csv, -1, 0) }
        assertFails { FlightRecordWindow.analyze(csv, 0, 1001) }
    }

    @Test fun rejectsMalformedDataEvenOutsideSelectedWindowAndPreservesQuotedAircraft() {
        assertFails { FlightRecordWindow.analyze(header + "0,1000,0,test,100\n1,2000,1000,test,NaN", 0, 0) }
        val csv = header + "0,1000,0,\"test, \"\"plane\"\"\",100"
        assertEquals("test, \"plane\"", FlightRecordWindow.analyze(csv, 0, 0).analysis.summary.aircraft)
    }
}
