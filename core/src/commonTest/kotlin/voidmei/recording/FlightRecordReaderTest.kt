package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class FlightRecordReaderTest {
    private val header = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,load_g"
    @Test fun summarizesRangesWithoutTurningMissingValuesIntoZero() {
        val summary = FlightRecordReader.summarize("$header\n0,10000,200,plane,300,1\n1,11000,1200,plane,,2\n3,14000,4200,plane,450,-1\n")
        assertEquals(3, summary.samples)
        assertEquals(4000L, summary.spanMs)
        assertEquals(RecordedRange(2, 300.0, 450.0), summary.ranges["ias_kmh"])
        assertEquals(RecordedRange(3, -1.0, 2.0), summary.ranges["load_g"])
        assertNull(summary.ranges["altitude_m"])
        assertEquals(10000L, summary.startEpochMs)
    }

    @Test fun parsesEscapedQuotesNewlinesCrLfBomAndReorderedColumns() {
        val summary = FlightRecordReader.summarize("\uFEFFaircraft,ias_kmh,elapsed_ms,sample_id,utc_epoch_ms,extra\r\n\"A,\"\"B\"\"\nC\",350,0,0,1000,unused\r\n")
        assertEquals("A,\"B\"\nC", summary.aircraft)
        assertEquals(350.0, summary.ranges["ias_kmh"]?.maximum)
        assertEquals(0L, summary.spanMs)
    }

    @Test fun readsActualWriterOutput() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":320,"H, m":1500}""",
            """{"valid":true,"type":"test"}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0))
        val text = FlightCsv.flightHeader + "\n" + FlightCsv.flightRow(0, 1000, 0, flight)
        val summary = FlightRecordReader.summarize(text)
        assertEquals("test", summary.aircraft)
        assertEquals(1500.0, summary.ranges["altitude_m"]?.minimum)
        assertEquals(320.0, summary.ranges["ias_kmh"]?.maximum)
    }

    @Test fun rejectsMalformedOrMixedFlightData() {
        for (text in listOf("", header, "$header\n0,1,0,plane,NaN,1", "$header\n0,1,0,plane,300",
            "$header\n0,1,0,\"plane,300,1", "$header\n0,1,0,\"plane\"oops,300,1",
            "$header\n0,1,0,plane,300,1\n0,2,1,plane,300,1", "$header\n0,1,5,plane,300,1\n1,2,0,plane,300,1",
            "$header\n0,1,0,plane,300,1\n1,2,1,other,300,1", FlightCsv.engineHeader,
            "$header,ias_kmh\n0,1,0,plane,300,1,300")) {
            assertFailsWith<IllegalArgumentException>(text) { FlightRecordReader.summarize(text) }
        }
    }

    @Test fun equalElapsedTimeIsAllowedButUtcNeedNotBeMonotonic() {
        val result = FlightRecordReader.summarize("$header\n0,20,0,plane,,\n1,10,0,plane,,")
        assertEquals(0L, result.spanMs)
        assertTrue(result.ranges.isEmpty())
        assertEquals(10L, result.endEpochMs)
    }
}
