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
            assertFailsWith<IllegalArgumentException> { scene.withLegacyPositions(imported.hudPositions) }
            val moved = scene.withLegacyPositions(imported.hudPositions, screenSize = LegacyScreenSize(1920, 1080)).regions.single()
            assertEquals(800, moved.x)
            assertEquals(60, moved.y)
        }
        for (attrs in listOf(":x NaN", ":y Infinity", ":x", ":x .2 :x .3", ":y nope"))
            assertFails { LegacySettingsReader.read("""(panel "飞行信息" $attrs)""") }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" :x .2)(panel "飞行信息" :y .4)""") }
    }
    @Test fun missingRegionsAreOptInBoundedAndIdempotent() {
        val imported = LegacySettingsReader.read("""(panel "地平仪" :x .9 :y .9)
            (panel "舵面值" :x -.2 :y .5)""")
        val original = HudRegion("region-1", HudRegionContent.FLIGHT, 0, 0, 200, 100,
            visible = false, fields = listOf("ias"), backgroundAlpha = .2f)
        val scene = HudSceneLayout(240, 120, listOf(original), enabled = false, displayId = "secondary")
        val current = AppSettings(hudSceneLayout = scene)
        assertEquals(current, imported.copy(createMissingHudRegions = true).applyTo(current))
        assertEquals(current, imported.copy(importHudPositions = true).applyTo(current))
        val chosen = imported.copy(importHudPositions = true, createMissingHudRegions = true)
        val result = chosen.applyTo(current)
        val updated = result.hudSceneLayout!!
        assertEquals(original, updated.regions.first())
        assertFalse(updated.enabled)
        assertEquals("secondary", updated.displayId)
        assertEquals(listOf("region-1", "region-2", "region-3"), updated.regions.map { it.id })
        updated.regions.drop(1).forEach {
            assertEquals(0, it.x)
            assertEquals(0, it.y)
            assertEquals(240, it.width)
            assertEquals(120, it.height)
            assertNull(it.fields)
            assertTrue(it.visible)
        }
        assertEquals(result, chosen.applyTo(result))
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(AppSettings(), chosen.applyTo(AppSettings()))
    }

    @Test fun insufficientCapacityRejectsWholeAdditionWhilePositionOnlyStillWorks() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x .2)(panel "地平仪" :x .5)""")
        val scene = HudSceneLayout(1000, 600, (1..32).map {
            HudRegion("region-$it", HudRegionContent.FLIGHT, 0, 0, 200, 100)
        })
        assertFailsWith<IllegalArgumentException> { scene.withLegacyPositions(imported.hudPositions, true) }
        assertEquals(200, scene.withLegacyPositions(imported.hudPositions).regions.first().x)
        val room = scene.copy(regions = scene.regions.dropLast(1))
        val completed = room.withLegacyPositions(imported.hudPositions, true)
        assertEquals(32, completed.regions.size)
        assertEquals(HudRegionContent.ATTITUDE, completed.regions.last().content)
        assertEquals(completed, completed.withLegacyPositions(imported.hudPositions, true))
    }

    @Test fun legacyPixelsAndRatiosAreConvertedIndependentlyUsingSourceScreen() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x 960 :y .25)
            (panel "地平仪" :x .25 :y 540)""").copy(importHudPositions = true)
        val flight = HudRegion("f", HudRegionContent.FLIGHT, 0, 0, 200, 100)
        val scene = HudSceneLayout(1000, 600, listOf(flight, flight.copy(id = "a", content = HudRegionContent.ATTITUDE)))
        assertFailsWith<IllegalArgumentException> { imported.applyToScene(scene) }
        val selected = imported.copy(legacyScreenSize = LegacyScreenSize(1920, 1080))
        val result = selected.applyToScene(scene)!!
        assertEquals(listOf(500 to 150, 250 to 300), result.regions.map { it.x to it.y })
        assertEquals(result, selected.applyToScene(result))
        val largerScreen = selected.copy(legacyScreenSize = LegacyScreenSize(3840, 2160)).applyToScene(scene)!!
        assertEquals(listOf(250 to 150, 250 to 150), largerScreen.regions.map { it.x to it.y })
        assertEquals(scene, selected.copy(importHudPositions = false).applyToScene(scene))
        assertEquals(result, HudSceneLayout.fromJson(result.toJson()))
    }

    @Test fun javaPixelThresholdIsStrictAndUnmatchedPositionsNeedNoScreen() {
        assertFalse(LegacyHudPosition(2.0, -100.0).needsScreenSize)
        assertTrue(LegacyHudPosition(2.0001, .1).needsScreenSize)
        assertEquals(LegacyHudPosition(2.0, -100.0), LegacyHudPosition(2.0, -100.0).normalized(null))
        assertEquals(LegacyHudPosition(.03, .1), LegacyHudPosition(3.0, .1).normalized(LegacyScreenSize(100, 100)))
        for ((w, h) in listOf(0 to 100, 100 to 0, -1 to 100)) assertFails { LegacyScreenSize(w, h) }
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("f", HudRegionContent.FLIGHT, 0, 0, 200, 100)))
        val positions = mapOf(HudRegionContent.ATTITUDE to LegacyHudPosition(960.0, 540.0))
        assertEquals(scene, scene.withLegacyPositions(positions))
        assertFails { scene.withLegacyPositions(positions, createMissing = true) }
        val added = scene.withLegacyPositions(positions, createMissing = true, screenSize = LegacyScreenSize(1920, 1080))
        assertEquals(500 to 300, added.regions.last().let { it.x to it.y })
    }

}
