package voidmei.telemetry

import kotlinx.serialization.json.*
import kotlin.test.*

class RecordedMapTest {
    @Test fun readsRepositoryCapturedMapEndpoints() {
        val capture = Json.parseToJsonElement(javaClass.getResource("/mock_scenarios/snapshots/recorded_p51d.json")!!.readText()).jsonObject
        val bounds = assertNotNull(MapTelemetryParser.info(capture.getValue("/map_info.json").toString()))
        val objects = MapTelemetryParser.objects(capture.getValue("/map_obj.json").toString())
        val snapshot = MapSnapshot(bounds, objects)
        assertEquals(65536.0, bounds.widthM)
        assertEquals(9, bounds.generation)
        assertNotNull(snapshot.player?.position)
        assertTrue(objects.any { it.type == "airfield" && it.start != null && it.end != null })
    }
}
