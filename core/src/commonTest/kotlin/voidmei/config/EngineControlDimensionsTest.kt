package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class EngineControlDimensionsTest {
    @Test fun dimensionsRoundTripAndMissingDimensionsKeepAdaptiveLayout() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 300, 200, engineControlDimensions = EngineControlDimensions(160, 16))
        val scene = HudSceneLayout(1000, 600, listOf(region))
        val settings = AppSettings(hudSceneLayout = scene)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        val json = scene.toJson().jsonObject
        val old = JsonObject(json + ("regions" to JsonArray(listOf(JsonObject(json.getValue("regions").jsonArray.single().jsonObject - "engineControlDimensions")))))
        assertNull(HudSceneLayout.fromJson(old).regions.single().engineControlDimensions)
        for (length in listOf(47, 513)) assertFails { EngineControlDimensions(length, 18) }
        for (thickness in listOf(7, 49)) assertFails { EngineControlDimensions(112, thickness) }
        assertFails { EngineControlDimensions.fromJson(Json.parseToJsonElement("""{"lengthDp":"160","thicknessDp":16}""")) }
        assertFails { EngineControlDimensions.fromJson(Json.parseToJsonElement("""{"lengthDp":160.5,"thicknessDp":16}""")) }
    }
}
