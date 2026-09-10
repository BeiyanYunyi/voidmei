package voidmei.recording

import kotlin.test.*

class RecordedReplayTest {
    @Test fun indexedFramesPreserveQuotedNewlinesBlankLinesAndMissingValues() {
        val text = "\uFEFFsample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,fuel_kg\r\n\r\n" +
            "0,2000,500,\"a,\"\"b\"\"\nplane\",100,50\r\n\r\n" +
            "1,1000,1500,\"a,\"\"b\"\"\nplane\",,40"
        val replay = RecordedReplay(text)
        assertEquals(2, replay.size)
        assertEquals(1000, replay.spanMs)
        assertEquals("a,\"b\"\nplane", replay.frame(0).aircraft)
        assertEquals(100.0, replay.frame(0).values["ias_kmh"])
        assertNull(replay.frame(1).values["ias_kmh"])
        assertEquals(40.0, replay.frame(1).values["fuel_kg"])
        assertEquals(1000, replay.frame(1).epochMs)
        assertEquals(1000, replay.frame(1).elapsedMs)
    }

    @Test fun timeSeekingHoldsPreviousFrameAndHandlesDuplicateTimestamps() {
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n" +
            "0,,0,test,100\n1,,1000,test,200\n2,,1000,test,300\n3,,5000,test,400\n"
        val replay = RecordedReplay(text)
        assertEquals(1..2, replay.indicesWithin(1000, 1000))
        assertNull(replay.indicesWithin(1001, 4999))
        assertEquals(1..3, replay.indicesWithin(1000, 5000))
        assertEquals(0, replay.indexAt(999))
        assertEquals(2, replay.indexAt(1000))
        assertEquals(2, replay.indexAt(4999))
        assertEquals(3, replay.indexAt(Long.MAX_VALUE))
        assertEquals(1, replay.indexOfSample(1))
        assertEquals(2, replay.indexOfSample(2))
        assertNull(replay.indexOfSample(20))
        assertNull(replay.indexOfSample(-1))
        assertEquals(200.0, replay.frame(1).values["ias_kmh"])
        assertEquals(300.0, replay.frame(2).values["ias_kmh"])
        assertFails { replay.indexAt(-1) }
        assertFails { replay.frame(4) }
    }

    @Test fun engineFieldsUseSameFrameWithoutFlightMetricAliases() {
        val replay = RecordedReplay("sample_id,utc_epoch_ms,elapsed_ms,aircraft,rpm,oil_temp_c\n0,1000,0,test,2000,90", listOf("rpm", "oil_temp_c"))
        assertEquals(mapOf("rpm" to 2000.0, "oil_temp_c" to 90.0), replay.frame(0).values)
    }
}
