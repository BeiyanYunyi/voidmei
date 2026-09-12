package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class EngineControlsLayoutTest {
    @Test fun everyLayoutRoundTripsIndependently() {
        val scene = HudSceneLayout(1000, 600, EngineControlsLayout.entries.mapIndexed { index, layout ->
            HudRegion("region-$index", HudRegionContent.ENGINE, index * 300, 0, 300, 200, engineControlsLayout = layout)
        })
        val settings = AppSettings(hudSceneLayout = scene)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }
    @Test fun oldBooleanMigratesAndNewEnumHasExplicitPrecedence() {
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 200)))
        val json = scene.toJson().jsonObject
        fun read(extra: Map<String, JsonElement>): EngineControlsLayout {
            val region = json.getValue("regions").jsonArray.single().jsonObject - "engineControlsLayout"
            return HudSceneLayout.fromJson(JsonObject(json + ("regions" to JsonArray(listOf(JsonObject(region + extra))))))
                .regions.single().engineControlsLayout
        }
        assertEquals(EngineControlsLayout.HORIZONTAL, read(emptyMap()))
        assertEquals(EngineControlsLayout.HORIZONTAL, read(mapOf("engineControlsVertical" to JsonPrimitive(false))))
        assertEquals(EngineControlsLayout.VERTICAL, read(mapOf("engineControlsVertical" to JsonPrimitive(true))))
        assertEquals(EngineControlsLayout.MIXED, read(mapOf("engineControlsVertical" to JsonPrimitive(true), "engineControlsLayout" to JsonPrimitive("MIXED"))))
        for (value in listOf(JsonPrimitive("true"), JsonPrimitive(1))) assertFails { read(mapOf("engineControlsVertical" to value)) }
        for (value in listOf(JsonPrimitive("unknown"), JsonPrimitive(true))) assertFails { read(mapOf("engineControlsLayout" to value)) }
    }
}
