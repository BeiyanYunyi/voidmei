package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class HudRegionBorderTest {
    @Test fun legacyBorderSelectionPreservesGeometryOpacityAndOtherRegions() {
        val mappings = mapOf("flightInfoEdge" to HudRegionContent.FLIGHT, "enableAxisEdge" to HudRegionContent.CONTROLS,
            "enableAttitudeIndicatorEdge" to HudRegionContent.ATTITUDE, "enablegearAndFlapsEdge" to HudRegionContent.MECHANIZATION)
        for ((key, type) in mappings) for (enabled in listOf(false, true)) {
            val first = HudRegion("one", type, 0, 0, 500, 250, borderEnabled = !enabled, borderAlpha = .25f)
            val before = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(first, first.copy(id = "two", y = 300))))
            val imported = LegacySettingsReader.read("""(panel p (item edge :target $key :type switch :value $enabled))""")
            assertFalse(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(before, imported.applyTo(before))
            val after = imported.copy(importHudRegionBorders = true).applyTo(before)
            assertEquals(first.copy(borderEnabled = enabled), after.hudSceneLayout!!.regions.first())
            assertEquals(before.hudSceneLayout!!.regions[1], after.hudSceneLayout!!.regions[1])
            assertEquals(after, SettingsJson.decode(SettingsJson.encode(after)))
            assertEquals(before, SettingsUndo.capture(before, after)!!.preview(after).settings)
            assertEquals(AppSettings(), imported.copy(importHudRegionBorders = true).applyTo(AppSettings()))
        }
    }

    @Test fun newlyCreatedRegionsAcceptBordersAndOldJsonDefaultsToNone() {
        val existing = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 500, 250)
        val scene = HudSceneLayout(1000, 600, listOf(existing))
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x .1 :y .1
            (item edge :target flightInfoEdge :type switch :value true))""")
            .copy(importHudRegionBorders = true, importHudPositions = true, createMissingHudRegions = true)
        assertTrue(imported.applyToScene(scene)!!.regions.last().borderEnabled)
        val old = scene.toJson().jsonObject.toMutableMap()
        old["regions"] = JsonArray(old.getValue("regions").jsonArray.map { JsonObject(it.jsonObject - "borderEnabled" - "borderAlpha") })
        assertEquals(scene, HudSceneLayout.fromJson(JsonObject(old)))
        for (alpha in listOf(-.1f, 1.1f, Float.NaN)) assertFails { existing.copy(borderAlpha = alpha) }
        assertFails { LegacySettingsReader.read("""(panel p (item e :target flightInfoEdge :type switch :value maybe))""") }
    }
}
