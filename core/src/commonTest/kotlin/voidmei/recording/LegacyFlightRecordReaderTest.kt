package voidmei.recording

import kotlin.test.*

class LegacyFlightRecordReaderTest {
    private fun row(minutes: String, ias: String = "300", altitude: String = "1500") =
        MutableList(32) { "0" }.apply {
            this[0] = minutes; this[2] = ias; this[5] = altitude; this[9] = "12"
            this[10] = "1.5"; this[29] = "-"; this[30] = "-2.5"; this[31] = ""
        }.joinToString(",")

    @Test fun convertsMinutesAndDoesNotInventAircraftOrUtc() {
        val text = LegacyFlightRecordReader.header + "\r\n" + row("0.5") + "\r\n" + row("1", "450", "2000")
        val result = LegacyFlightRecordReader.analyze(text)
        assertEquals(30000L, result.summary.spanMs)
        assertEquals("", result.summary.aircraft)
        assertNull(result.summary.startEpochMs)
        assertNull(result.summary.endEpochMs)
        assertEquals(RecordedRange(2, 300.0, 450.0), result.summary.ranges["ias_kmh"])
        assertEquals(2000.0, result.summary.ranges["altitude_m"]?.maximum)
        assertEquals(12.0, result.summary.ranges["sep_mps"]?.maximum)
        assertNull(result.summary.ranges["aoa_deg"])
        assertEquals(-2.5, result.summary.ranges["sideslip_deg"]?.minimum)
        assertNull(result.summary.ranges["roll_rate_degps"])
        assertEquals(2, result.notes.size)
    }

    @Test fun rejectsOtherHeadersInsteadOfGuessingTimeUnits() {
        assertFails { LegacyFlightRecordReader.analyze(LegacyFlightRecordReader.header.replace("时间/s", "time/s") + "\n" + row("1")) }
        assertFails { LegacyFlightRecordReader.analyze(FlightCsv.flightHeader) }
    }

    @Test fun rejectsMalformedTimeAndRows() {
        for (data in listOf(row("NaN"), row("-1"), row("1e308"), row("1", "NaN"), row("1").dropLast(1),
            row("2") + "\n" + row("1"))) {
            assertFails { LegacyFlightRecordReader.analyze(LegacyFlightRecordReader.header + "\n" + data) }
        }
    }
}
