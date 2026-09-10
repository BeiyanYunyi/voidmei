package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class TemperatureRecordingTest {
    @Test fun cockpitTemperaturesRemainSeparateFromEngineCelsiusAndOldFilesStayReadable() {
        val base = TelemetryParser.parse("""{"valid":true,"water temp 1, C":90}""",
            """{"valid":true,"water_temperature":100,"head_temperature":200,"oil_temperature":-20}""")!!
        val samples = listOf(base, base.copy(waterTemperatureRaw = null, headTemperatureRaw = null, oilTemperatureRaw = null),
            base.copy(waterTemperatureRaw = 0.0))
        val csv = FlightCsv.flightHeader + "\n" + samples.mapIndexed { index, telemetry ->
            FlightCsv.flightRow(index.toLong(), index * 1000L, index * 1000L,
                ConnectionState.Flying(telemetry, FlightMetrics()))
        }.joinToString("\n")
        val replay = RecordedReplay(csv)
        assertEquals(100.0, replay.frame(0).values["water_temperature_raw"])
        assertEquals(200.0, replay.frame(0).values["head_temperature_raw"])
        assertEquals(-20.0, replay.frame(0).values["oil_temperature_raw"])
        assertNull(replay.frame(1).values["water_temperature_raw"])
        assertEquals(0.0, replay.frame(2).values["water_temperature_raw"])
        val points = FlightRecordPlots.analyze(csv).plots.getValue("water_temperature_raw")
        assertEquals(listOf(100.0, 0.0), points.map { it.value })
        assertFalse(points.last().connectFromPrevious)
        val old = csv.lines().joinToString("\n") { it.split(',').take(37).joinToString(",") }
        assertNull(RecordedReplay(old).frame(0).values["water_temperature_raw"])
        assertNull(FlightRecordPlots.analyze(old).plots["water_temperature_raw"])
    }
}
