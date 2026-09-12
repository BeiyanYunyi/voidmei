package voidmei.config

import kotlin.test.*

class LegacyHudPositionTest {
    @Test fun optInMovesOnlyFirstMatchingRegionAndRetainsSceneAndRegionSettings() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x .25 :y .5 :alpha 1 :visible false)
            (panel "地平仪" :x .9 :y -.3)
            (panel "MiniHUD" :x .8 :y .8)""")
        val flight = HudRegion("flight", HudRegionContent.FLIGHT, 10, 20, 200, 120,
            backgroundAlpha = .8f, contentAlpha = .7f, fields = listOf("ias"), visible = false)
        val scene = HudSceneLayout(1000, 600, listOf(flight, flight.copy(id = "duplicate"),
            HudRegion("attitude", HudRegionContent.ATTITUDE, 20, 30, 340, 204)), enabled = false, displayId = "secondary")
        val current = AppSettings(hudSceneLayout = scene)
        assertFalse(imported.hasChanges)
        assertEquals(current, imported.applyTo(current))
        val chosen = imported.copy(importHudPositions = true)
        val expected = current.copy(hudSceneLayout = scene.copy(regions = listOf(
            flight.copy(x = 250, y = 300), flight.copy(id = "duplicate"), scene.regions[2].copy(x = 660, y = 0))))
        assertEquals(expected, chosen.applyTo(current))
        assertEquals(expected, chosen.applyTo(expected))
        assertEquals(expected, SettingsJson.decode(SettingsJson.encode(expected)))
        assertEquals(AppSettings(), chosen.applyTo(AppSettings()))
    }

    @Test fun coordinatesUseLegacyDefaultsRejectAmbiguityAndSafelyClampOffScreenWindows() {
        for (panel in listOf("飞行信息", "地平仪", "舵面值", "起落襟翼")) {
            val imported = LegacySettingsReader.read("""(panel "$panel" :x 1e300)""")
            assertEquals(LegacyHudPosition(1e300, .1), imported.hudPositions.values.single())
            val content = imported.hudPositions.keys.single()
            val scene = HudSceneLayout(1000, 600, listOf(HudRegion("r", content, 0, 0, 200, 120)))
            val moved = scene.withLegacyPositions(imported.hudPositions).regions.single()
            assertEquals(800, moved.x)
            assertEquals(60, moved.y)
        }
        for (attrs in listOf(":x NaN", ":y Infinity", ":x", ":x .2 :x .3", ":y nope"))
            assertFails { LegacySettingsReader.read("""(panel "飞行信息" $attrs)""") }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" :x .2)(panel "飞行信息" :y .4)""") }
    }
}
