package voidmei.desktop

import kotlin.test.*
import voidmei.fm.FlightModelParameters
import java.time.Instant

class ModelComparisonExportTest {
    @Test fun exportContainsConditionsSourcesQuotedNamesAndUnknownCells() {
        val left = NamedModel("left,\"one\"", FlightModelParameters(1000.0, null, emptyList(), false, emptyList()),
            source = "/模型/first.blkx", capturedAt = Instant.ofEpochMilli(1000))
        val right = left.copy(aircraft = "right", parameters = left.parameters.copy(emptyMassKg = 1300.0), source = "/模型/second.blkx")
        val csv = modelComparisonCsv(left, right, 0.25, 50.0, 0.5)
        assertTrue(csv.startsWith("\"metric\",\"unit\",\"baseline_aircraft\""))
        assertTrue(csv.contains("\"left,\"\"one\"\"\""))
        assertTrue(csv.contains("\"1000.0\",\"1300.0\",\"300.0\",\"0.25\",\"50.0\",\"0.5\""))
        assertTrue(csv.contains("\"/模型/first.blkx\",\"/模型/second.blkx\",\"1970-01-01T00:00:01Z\""))
        assertTrue(csv.lines().first { it.startsWith("\"比较燃油量\"") }.contains("\"right\",\"\",\"\",\"\""))
    }
}
