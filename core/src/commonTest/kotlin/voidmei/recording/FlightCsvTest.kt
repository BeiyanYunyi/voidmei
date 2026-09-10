package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class FlightCsvTest {
    private fun flight() = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true,"IAS, km/h":123.5,"thrust 14, kgs":100}""",
        """{"valid":true,"type":"test"}""")!!, FlightMetrics())

    @Test fun columnCountsAndMissingValuesAreStable() {
        val row = FlightCsv.flightRow(5, 1000, 200, flight()).split(',')
        assertEquals(FlightCsv.flightHeader.split(',').size, row.size)
        assertEquals(listOf("5", "1000", "200", "\"test\"", "123.5", ""), row.take(6))
        assertFalse(row.contains("null"))
        val engines = FlightCsv.engineRows(5, 1000, flight().telemetry.engines)
        assertEquals(1, engines.size)
        assertEquals(FlightCsv.engineHeader.split(',').size, engines.single().split(',').size)
        assertEquals("14", engines.single().split(',')[2])
    }

    @Test fun textEscapesAndSpreadsheetFormulasAreNotExecutable() {
        val base = flight()
        val row = FlightCsv.flightRow(0, 0, 0, base.copy(telemetry = base.telemetry.copy(aircraft = "=test,\"飞机\"\n")))
        assertTrue(row.contains("\"'=test,\"\"飞机\"\"\n\""))
    }

    @Test fun nonFiniteValuesAreEmptyAndNegativeNumbersRemainNumeric() {
        val base = flight()
        val row = FlightCsv.flightRow(0, 0, 0, base.copy(telemetry = base.telemetry.copy(iasKmh = Double.NaN, verticalSpeedMps = -12.5)))
        assertFalse(row.contains("NaN"))
        assertTrue(row.contains(",-12.5,"))
    }
}
