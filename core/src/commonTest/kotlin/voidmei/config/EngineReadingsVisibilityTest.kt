package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class EngineReadingsVisibilityTest {
    @Test fun visibilityRoundTripsAndOldScenesKeepTables() {
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 300, 200, showEngineReadings = false)))
        val settings = AppSettings(hudSceneLayout = scene)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        val root = scene.toJson().jsonObject
        val region = root.getValue("regions").jsonArray.single().jsonObject - "showEngineReadings"
        val old = JsonObject(root + ("regions" to JsonArray(listOf(JsonObject(region)))))
        assertTrue(HudSceneLayout.fromJson(old).regions.single().showEngineReadings)
        assertFails { HudSceneLayout.fromJson(JsonObject(root + ("regions" to JsonArray(listOf(JsonObject(region +
            ("showEngineReadings" to JsonPrimitive("false")))))))) }
    }
    @Test fun newLegacyControlsUseMixedInstrumentsWhilePowerKeepsReadings() {
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)))
        val control = scene.legacyEngineRegion("enableEngineControl")
        assertFalse(control.showEngineReadings)
        assertTrue(control.showEngineInstruments)
        assertEquals(EngineControlsLayout.MIXED, control.engineControlsLayout)
        assertTrue(scene.legacyEngineRegion("engineInfoSwitch").showEngineReadings)
    }
}
