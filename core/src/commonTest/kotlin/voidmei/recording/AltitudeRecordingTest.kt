package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class AltitudeRecordingTest {
    private fun sample(radio: Double?, climb: Double?) = ConnectionState.Flying(TelemetryParser.parse(
        """{"valid":true,"H, m":1000}""", """{"valid":true,"type":"test"}""")!!.copy(
            radioAltitudeRaw = radio, verticalSpeedMps = climb), FlightMetrics())

    @Test fun appendedRadarColumnPreservesOldColumnOrderAndRoundTripsWithoutConversion() {
        val header = FlightCsv.flightHeader.split(',')
        assertEquals("thrust_power_kw", header[32])
        assertEquals("radio_altitude_raw", header[33])
        val csv = FlightCsv.flightHeader + "\n" + FlightCsv.flightRow(0, 1000, 0, sample(328.084, -2.0)) +
            "\n" + FlightCsv.flightRow(1, 2000, 1000, sample(300.0, -3.0))
        val analysis = FlightRecordPlots.analyze(csv)
        assertEquals(RecordedRange(2, 300.0, 328.084), analysis.summary.ranges["radio_altitude_raw"])
        assertEquals(listOf(328.084, 300.0), analysis.plots.getValue("radio_altitude_raw").map { it.value })
        assertEquals(listOf(-2.0, -3.0), analysis.plots.getValue("vertical_speed_mps").map { it.value })
    }

    @Test fun olderCsvRemainsReadableAndMissingRadarDoesNotBecomeAltitude() {
        val csv = FlightCsv.flightHeader + "\n" + FlightCsv.flightRow(0, 1000, 0, sample(null, null))
        val analysis = FlightRecordPlots.analyze(csv)
        assertEquals(1000.0, analysis.summary.ranges["altitude_m"]?.minimum)
        assertNull(analysis.summary.ranges["radio_altitude_raw"])
        assertTrue(analysis.plots.getValue("radio_altitude_raw").isEmpty())
        val oldCsv = csv.lines().joinToString("\n") { it.split(',').take(33).joinToString(",") }
        val old = FlightRecordPlots.analyze(oldCsv)
        assertEquals(analysis.summary, old.summary)
        assertNull(old.plots["radio_altitude_raw"])
    }

    @Test fun radarMissingSamplesBreakHistoricalCurve() {
        val csv = FlightCsv.flightHeader + "\n" + listOf(100.0, null, 50.0).mapIndexed { i, radar ->
            FlightCsv.flightRow(i.toLong(), 1000L + i * 1000, i * 1000L, sample(radar, 0.0))
        }.joinToString("\n")
        val points = FlightRecordPlots.analyze(csv).plots.getValue("radio_altitude_raw")
        assertEquals(2, points.size)
        assertTrue(points.none { it.connectFromPrevious })
    }
}
