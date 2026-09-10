package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class AltimeterRecordingTest {
    @Test fun rawInstrumentScaleSurvivesRecordingReplayAndCroppingWithoutChangingMetres() {
        val samples = listOf("3280.84", "null", "0", "-10", "-65535", "\"invalid\"").map { raw ->
            TelemetryParser.parse("""{"valid":true,"H, m":1000}""",
                """{"valid":true,"type":"test","altitude_10k":$raw}""")!!
        }
        val csv = FlightCsv.flightHeader + "\n" + samples.mapIndexed { index, t ->
            FlightCsv.flightRow(index.toLong(), index * 1000L, index * 1000L,
                ConnectionState.Flying(t, FlightMetrics()))
        }.joinToString("\n")
        val replay = RecordedReplay(csv)
        val expected = listOf(3280.84, null, 0.0, -10.0, null, null)
        expected.forEachIndexed { index, value ->
            assertEquals(value, replay.frame(index).values["altimeter_raw"])
            assertEquals(1000.0, replay.frame(index).values["altitude_m"])
        }
        val points = FlightRecordPlots.analyze(csv).plots.getValue("altimeter_raw")
        assertEquals(listOf(3280.84, 0.0, -10.0), points.map { it.value })
        assertFalse(points[1].connectFromPrevious)
        assertTrue(points[2].connectFromPrevious)
        val cropped = RecordedReplay(FlightRecordWindow.analyze(csv, 1000, 3000).text)
        assertNull(cropped.frame(0).values["altimeter_raw"])
        assertEquals(0.0, cropped.frame(1).values["altimeter_raw"])
        assertEquals(-10.0, cropped.frame(2).values["altimeter_raw"])
        val old = csv.lines().joinToString("\n") { it.split(',').take(40).joinToString(",") }
        assertNull(RecordedReplay(old).frame(0).values["altimeter_raw"])
        assertNull(FlightRecordPlots.analyze(old).plots["altimeter_raw"])
    }
}
