package voidmei.telemetry

import kotlin.test.*
import voidmei.config.*
import voidmei.recording.*

class OilPressureTest {
    private fun parse(raw: String = "2.75") = TelemetryParser.parse(
        """{"valid":true,"oil pressure 1, atm":99,"throttle 1, %":100,"throttle 2, %":100}""",
        """{"valid":true,"oil_pressure":$raw,"oil_pressure1":42,"fuel_pressure":9.7}""")!!

    @Test fun exactUnnumberedGaugePreservesZeroAndRejectsInvalidReadings() {
        val telemetry = parse()
        assertEquals(2.75, telemetry.oilPressureRaw)
        assertEquals(9.7, telemetry.fuelPressureRaw)
        assertEquals(2, telemetry.engines.size)
        assertEquals(0.0, parse("0").oilPressureRaw)
        for (raw in listOf("null", "-1", "-65535", "1e999", "true", "\"2.75\"", "{}", "[]"))
            assertNull(parse(raw).oilPressureRaw, raw)
        assertNull(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"oil_pressure1":42}""")!!.oilPressureRaw)
    }

    @Test fun optionalHudFieldKeepsRawUnitsAndPersists() {
        val field = HudField.OIL_PRESSURE_RAW
        val settings = AppSettings(hudFields = listOf(field.id, "ias"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFalse(field.id in HudField.defaults)
        assertEquals("仪表单位", field.unitFor(parse()))
        assertEquals(2.75, field.value(ConnectionState.Flying(parse(), FlightMetrics())))
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(field.value(ConnectionState.Flying(parse().copy(oilPressureRaw = value), FlightMetrics())))
    }

    @Test fun appendedCsvColumnSupportsAnalysisReplayAndOldFiles() {
        val values = listOf(2.75, null, 0.0, -1.0, Double.NaN)
        val csv = FlightCsv.flightHeader + "\n" + values.mapIndexed { index, value ->
            FlightCsv.flightRow(index.toLong(), 1000L + index * 1000, index * 1000L,
                ConnectionState.Flying(parse().copy(oilPressureRaw = value), FlightMetrics()))
        }.joinToString("\n")
        assertEquals("oil_pressure_raw", FlightCsv.flightHeader.split(',').last())
        assertEquals("wep_time_upper_s", FlightCsv.flightHeader.split(',').dropLast(1).last())
        val analysis = FlightRecordPlots.analyze(csv)
        assertEquals(RecordedRange(2, 0.0, 2.75), analysis.summary.ranges["oil_pressure_raw"])
        val points = analysis.plots.getValue("oil_pressure_raw")
        assertEquals(listOf(2.75, 0.0), points.map { it.value })
        assertTrue(points.none { it.connectFromPrevious })
        val replay = RecordedReplay(csv)
        values.indices.forEach { index ->
            assertEquals(values[index]?.takeIf { it.isFinite() && it >= 0 }, replay.frame(index).values["oil_pressure_raw"])
        }
        val old = csv.lines().joinToString("\n") { it.substringBeforeLast(',') }
        val previous = FlightRecordPlots.analyze(old)
        assertEquals(5, previous.summary.samples)
        assertNull(previous.plots["oil_pressure_raw"])
        assertNull(RecordedReplay(old).frame(0).values["oil_pressure_raw"])
    }
}
