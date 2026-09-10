package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class FuelPressureRecordingTest {
    private fun csv(values: List<Double?>): String = FlightCsv.flightHeader + "\n" + values.mapIndexed { index, pressure ->
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
            .copy(fuelPressureRaw = pressure)
        FlightCsv.flightRow(index.toLong(), 1000L + index * 1000, index * 1000L,
            ConnectionState.Flying(telemetry, FlightMetrics()))
    }.joinToString("\n")

    @Test fun appendedColumnPreservesExistingLayoutAndRawValues() {
        assertEquals("radio_altitude_raw", FlightCsv.flightHeader.split(',')[33])
        assertEquals("fuel_pressure_raw", FlightCsv.flightHeader.split(',')[34])
        val analysis = FlightRecordPlots.analyze(csv(listOf(10.0, 9.7, 0.0)))
        assertEquals(RecordedRange(3, 0.0, 10.0), analysis.summary.ranges["fuel_pressure_raw"])
        assertEquals(listOf(10.0, 9.7, 0.0), analysis.plots.getValue("fuel_pressure_raw").map { it.value })
        assertEquals(listOf(0L, 1L, 2L), analysis.plots.getValue("fuel_pressure_raw").map { it.sampleId })
        val replay = RecordedReplay(csv(listOf(10.0, null, 0.0)))
        assertEquals(10.0, replay.frame(0).values["fuel_pressure_raw"])
        assertNull(replay.frame(1).values["fuel_pressure_raw"])
        assertEquals(0.0, replay.frame(2).values["fuel_pressure_raw"])
    }

    @Test fun unknownAndNonfiniteSamplesStayBlankAndBreakCurve() {
        val text = csv(listOf(10.0, null, 9.0, Double.NaN, Double.POSITIVE_INFINITY, 0.0))
        val points = FlightRecordPlots.analyze(text).plots.getValue("fuel_pressure_raw")
        assertEquals(listOf(10.0, 9.0, 0.0), points.map { it.value })
        assertTrue(points.none { it.connectFromPrevious })
        assertEquals("", text.lines()[2].split(',')[34])
    }

    @Test fun previousThirtyFourColumnRecordingsRemainReadableWithoutInventingPressure() {
        val old = csv(listOf(null)).lines().joinToString("\n") { it.split(',').take(34).joinToString(",") }
        val analysis = FlightRecordPlots.analyze(old)
        assertEquals(1, analysis.summary.samples)
        assertNull(analysis.summary.ranges["fuel_pressure_raw"])
        assertNull(analysis.plots["fuel_pressure_raw"])
    }
}
