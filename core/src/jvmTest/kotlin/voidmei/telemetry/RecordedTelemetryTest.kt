package voidmei.telemetry

import kotlinx.serialization.json.*
import kotlin.test.*

class RecordedTelemetryTest {
    @Test fun capturedP51cRetainsHighLoadFlightAndMap() {
        val capture = Json.parseToJsonElement(javaClass.getResource("/mock_scenarios/snapshots/recorded_p51c.json")!!.readText()).jsonObject
        val telemetry = TelemetryParser.parse(capture.getValue("/state").toString(), capture.getValue("/indicators").toString())!!
        assertEquals("p-51c-10-nt", telemetry.aircraft)
        assertEquals(380.0, telemetry.iasKmh)
        assertEquals(382.0, telemetry.tasKmh)
        assertEquals(4.57, telemetry.loadG)
        assertEquals(12.9, telemetry.angleOfAttackDeg)
        assertEquals(220.0, telemetry.fuelKg)
        assertEquals(1471.3, telemetry.engines.single().powerHp)
        val bounds = assertNotNull(MapTelemetryParser.info(capture.getValue("/map_info.json").toString()))
        assertEquals(131072.0, bounds.widthM)
        assertEquals(1, bounds.generation)
        assertNotNull(MapSnapshot(bounds, MapTelemetryParser.objects(capture.getValue("/map_obj.json").toString())).player)
    }

    @Test fun originalMockCaptureRetainsFlightAndEngineValues() {
        val capture = Json.parseToJsonElement(javaClass.getResource("/mock_scenarios/snapshots/recorded_p51d.json")!!.readText()).jsonObject
        val telemetry = TelemetryParser.parse(capture.getValue("/state").toString(), capture.getValue("/indicators").toString())!!
        assertEquals("p-51d-20_china", telemetry.aircraft)
        assertEquals(474.0, telemetry.iasKmh)
        assertEquals(454.0, telemetry.tasKmh)
        assertEquals(197.0, telemetry.fuelKg)
        assertEquals(734.0, telemetry.fuelCapacityKg)
        val engine = telemetry.engines.single()
        assertEquals(110.0, engine.throttlePercent)
        assertEquals(1597.8, engine.powerHp)
        assertEquals(840.0, engine.thrustKgf)
        assertEquals(3001.0, engine.rpm)
    }
}
