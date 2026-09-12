package voidmei.config

import kotlin.test.*

class LegacyHudVisibilityTest {
    @Test fun independentSwitchesRequireOptInAndOnlyAffectFirstMatchingRegions() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :visible true
            (item x :target flightInfoSwitch :type switch :value false)
            (item x :target enableAxis :type switch-inv :value false)
            (item x :target enableAttitudeIndicator :type switch :value false)
            (item x :target enablegearAndFlaps :type switch :value true))""")
        assertEquals(mapOf(HudRegionContent.FLIGHT to false, HudRegionContent.CONTROLS to true,
            HudRegionContent.ATTITUDE to false, HudRegionContent.MECHANIZATION to true), imported.hudRegionVisibility)
        assertTrue(imported.unmigrated.isEmpty())
        assertFalse(imported.hasChanges)
        val regions = HudRegionContent.entries.mapIndexed { index, content ->
            HudRegion("r-$index", content, 5, 10, 200, 100, visible = content != HudRegionContent.CONTROLS,
                backgroundAlpha = .3f, contentAlpha = .8f, fields = listOf("future"))
        }
        val current = AppSettings(hudEnabled = false, hudAttitude = true,
            hudSceneLayout = HudSceneLayout(1000, 600, regions + regions.first().copy(id = "duplicate"),
                enabled = false, displayId = "secondary"))
        assertEquals(current, imported.applyTo(current))
        val selected = imported.copy(importHudRegionVisibility = true)
        assertTrue(selected.hasChanges)
        val result = selected.applyTo(current)
        val expectedRegions = regions.map { it.copy(visible = imported.hudRegionVisibility[it.content] ?: it.visible) } +
            regions.first().copy(id = "duplicate")
        assertEquals(current.copy(hudSceneLayout = current.hudSceneLayout!!.copy(regions = expectedRegions)), result)
        assertEquals(result, selected.applyTo(result))
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(AppSettings(), selected.applyTo(AppSettings()))
    }

    @Test fun newlyCreatedRegionCanBeHiddenWithoutMovingOtherRegions() {
        val imported = LegacySettingsReader.read("""(panel "舵面值" :x .8 :y .5
            (item x :target enableAxis :type switch :value false))""").copy(
                importHudPositions = true, createMissingHudRegions = true, importHudRegionVisibility = true)
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 200, 100)))
        val result = imported.applyToScene(scene)!!
        assertEquals(scene.regions.single(), result.regions.first())
        assertEquals(HudRegionContent.CONTROLS, result.regions.last().content)
        assertEquals(560, result.regions.last().x)
        assertEquals(300, result.regions.last().y)
        assertFalse(result.regions.last().visible)
        assertTrue(imported.copy(importHudRegionVisibility = false).applyToScene(scene)!!.regions.last().visible)
        assertEquals(scene, imported.copy(importHudPositions = false).applyToScene(scene))
    }

    @Test fun malformedSwitchesAndDuplicateTargetsAreRejected() {
        for (target in listOf("flightInfoSwitch", "enableAxis", "enableAttitudeIndicator", "enablegearAndFlaps")) {
            for (attributes in listOf(":type switch :value nope", ":type data :value true", ":type switch", ":value true"))
                assertFails { LegacySettingsReader.read("(panel p (item x :target $target $attributes))") }
            assertFails { LegacySettingsReader.read("""(panel p
                (item x :target "$target" :type switch :value true)
                (item y :target "$target" :type switch :value false))""") }
        }
    }
}
