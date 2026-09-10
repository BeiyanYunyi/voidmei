package voidmei.recording

import kotlin.test.*
import voidmei.telemetry.*

class BoosterRecordingTest {
    @Test fun boosterValuesStaySeparateAcrossReplayGapsAndCrop() {
        val samples = listOf("50" to "200", "null" to "null", "0" to "200", "25" to "null")
        val csv = FlightCsv.flightHeader + "\n" + samples.mapIndexed { index, (fuel, capacity) ->
            val t = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":500,"Mfuel0, kg":800,
                "Mfuel 1, kg":$fuel,"Mfuel0 1, kg":$capacity,"Mfuel 10, kg":999}""",
                """{"valid":true,"type":"test"}""")!!
            FlightCsv.flightRow(index.toLong(), 10000 + index * 1000L, index * 1000L, ConnectionState.Flying(t, FlightMetrics()))
        }.joinToString("\n")
        val replay = RecordedReplay(csv)
        for (index in samples.indices) {
            assertEquals(500.0, replay.frame(index).values["fuel_kg"])
            assertEquals(800.0, replay.frame(index).values["fuel_capacity_kg"])
            assertEquals(samples[index].first.toDoubleOrNull(), replay.frame(index).values["booster_fuel_kg"])
            assertEquals(samples[index].second.toDoubleOrNull(), replay.frame(index).values["booster_fuel_capacity_kg"])
        }
        val points = FlightRecordPlots.analyze(csv).plots.getValue("booster_fuel_kg")
        assertEquals(listOf(50.0, 0.0, 25.0), points.map { it.value })
        assertFalse(points[1].connectFromPrevious)
        val crop = RecordedReplay(FlightRecordWindow.analyze(csv, 1000, 2000).text)
        assertNull(crop.frame(0).values["booster_fuel_kg"])
        assertEquals(0.0, crop.frame(1).values["booster_fuel_kg"])
        val old = csv.lines().joinToString("\n") { it.split(',').take(42).joinToString(",") }
        assertNull(RecordedReplay(old).frame(0).values["booster_fuel_kg"])
        assertNull(FlightRecordPlots.analyze(old).plots["booster_fuel_capacity_kg"])
    }
}
