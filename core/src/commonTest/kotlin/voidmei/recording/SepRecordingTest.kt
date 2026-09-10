package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class SepRecordingTest {
    @Test fun analysisAndReplayPreserveSmoothedSepAndMissingFrames() {
        val calculator = FlightCalculator()
        val expected = mutableListOf<Double?>()
        val rows = (0..40).map { index ->
            val time = index * 100L
            val telemetry = TelemetryParser.parse(
                """{"valid":true,"TAS, km/h":${360 + index / 2},"Vy, m/s":1}""",
                """{"valid":true,"type":"test","aviahorizon_pitch":-10}""",
            )!!.let { if (index == 20) it.copy(tasKmh = null) else it }
            val metrics = calculator.update(telemetry, time)
            expected += metrics.specificExcessPowerMps
            FlightCsv.flightRow(index.toLong(), 100000 + time, time, ConnectionState.Flying(telemetry, metrics))
        }
        val csv = (listOf(FlightCsv.flightHeader) + rows).joinToString("\n")
        val replay = RecordedReplay(csv)
        expected.forEachIndexed { index, sep ->
            assertEquals(sep, replay.frame(index).values["sep_mps"])
            assertEquals(-10.0, replay.frame(index).values["pitch_deg"])
        }
        assertNull(expected[0])
        assertNull(expected[20])
        assertNull(expected[21])
        val analysis = FlightRecordPlots.analyze(csv, buckets = 100)
        val valid = expected.filterNotNull()
        val range = assertNotNull(analysis.summary.ranges["sep_mps"])
        assertEquals(valid.min(), range.minimum)
        assertEquals(valid.max(), range.maximum)
        val points = analysis.plots.getValue("sep_mps")
        points.forEach { point -> assertEquals(expected[point.sampleId!!.toInt()], point.value) }
        assertFalse(points.first { it.sampleId == 22L }.connectFromPrevious)
        // Adjacent source speeds still repeat, while stored SEP retains the measured trend.
        assertEquals(replay.frame(30).values["tas_kmh"], replay.frame(31).values["tas_kmh"])
        assertTrue(assertNotNull(replay.frame(31).values["sep_mps"]) > 10.0)
    }
}
