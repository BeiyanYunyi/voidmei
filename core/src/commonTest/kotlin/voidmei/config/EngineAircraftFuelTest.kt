package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class EngineAircraftFuelTest {
    @Test fun fuelIsOptInAndRoundTripsWithStrictTypes() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 300, 200, showAircraftFuel = true)
        val scene = HudSceneLayout(1000, 600, listOf(region))
        val settings = AppSettings(hudSceneLayout = scene)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        val root = scene.toJson().jsonObject
        val oldRegion = root.getValue("regions").jsonArray.single().jsonObject - "showAircraftFuel"
        fun withRegion(r: JsonObject) = JsonObject(root + ("regions" to JsonArray(listOf(r))))
        assertFalse(HudSceneLayout.fromJson(withRegion(JsonObject(oldRegion))).regions.single().showAircraftFuel)
        assertFails { HudSceneLayout.fromJson(withRegion(JsonObject(oldRegion + ("showAircraftFuel" to JsonPrimitive("true"))))) }
    }
}
