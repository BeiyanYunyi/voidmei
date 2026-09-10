package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.Engine

class EngineRecordReaderTest {
    @Test fun recordedEnginesAreSummarizedSeparatelyWithMissingValues() {
        fun engine(index: Int, rpm: Double?, thrust: Double) = Engine(index, 100.0, rpm, 1200.0, thrust, null, 90.0)
        val text = FlightCsv.engineHeader + "\n" + (
            FlightCsv.engineRows(0, 1000, listOf(engine(2, 2000.0, 200.0), engine(1, 1000.0, 100.0))) +
                FlightCsv.engineRows(1, 900, listOf(engine(1, null, 150.0), engine(2, 2200.0, 250.0)))
            ).joinToString("\n")
        val records = EngineRecordReader.summarize(text)
        assertEquals(listOf(1, 2), records.map { it.index })
        assertEquals(RecordedRange(1, 1000.0, 1000.0), records[0].ranges["rpm"])
        assertEquals(RecordedRange(2, 2000.0, 2200.0), records[1].ranges["rpm"])
        assertEquals(RecordedRange(2, 100.0, 150.0), records[0].ranges["thrust_kgf"])
        assertNull(records[0].ranges["water_temp_c"])
        assertEquals(900, records[0].lastEpochMs) // Wall clock may move backwards; no inferred duration.
    }

    @Test fun reorderedColumnsAndSparseEngineSamplesAreAccepted() {
        val records = EngineRecordReader.summarize("engine_index,rpm,utc_epoch_ms,sample_id\n10,1000,1000,0\n1,2000,1200,2\n10,1500,1400,4\n")
        assertEquals(1, records.first().samples)
        assertEquals(2, records.last().samples)
        assertEquals(4, records.last().lastSampleId)
    }

    @Test fun rejectsDuplicateSamplesBadMetadataAndMalformedMetrics() {
        val header = "sample_id,utc_epoch_ms,engine_index,rpm\n"
        for (rows in listOf("0,1000,1,100\n0,1000,1,200", "0,1000,1,100\n0,1001,2,200",
            "1,1000,1,100\n0,1000,2,200", "0,1000,0,100", "0,1000,1,NaN", "0,,1,100", "0,1000,1")) {
            assertFails { EngineRecordReader.summarize(header + rows) }
        }
        assertFails { EngineRecordReader.summarize(FlightCsv.engineHeader) }
        assertFails { EngineRecordReader.summarize("sample_id,sample_id,utc_epoch_ms,engine_index,rpm\n0,0,1000,1,100") }
    }
}
