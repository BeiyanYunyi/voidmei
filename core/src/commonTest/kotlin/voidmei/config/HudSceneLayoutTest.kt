package voidmei.config

import kotlin.test.*

class HudSceneLayoutTest {
    @Test fun addAndRemoveRegionsKeepIdentitiesBoundsAndOtherSettings() {
        val base = HudSceneLayout(240, 120, listOf(HudRegion("region-1", HudRegionContent.FLIGHT, 0, 0, 240, 120, .3f)))
        val first = base.addRegion(HudRegionContent.ENGINE)
        val second = first.addRegion(HudRegionContent.ENGINE)
        assertEquals(listOf("region-1", "region-2", "region-3"), second.regions.map { it.id })
        assertEquals(listOf(1, 2), second.regions.filter { it.content == HudRegionContent.ENGINE }.map { it.engineIndex })
        assertEquals(base.regions.first(), second.regions.first())
        assertTrue(second.regions.all { it.x + it.width <= second.width && it.y + it.height <= second.height })
        val removed = second.removeRegion("region-2")
        assertEquals(listOf(base.regions.first(), second.regions.last()), removed.regions)
        assertEquals(removed, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = removed))).hudSceneLayout)
        assertFailsWith<IllegalArgumentException> { base.removeRegion("region-1") }
        var full = base
        repeat(31) { full = full.addRegion(HudRegionContent.ATTITUDE) }
        assertEquals(32, full.regions.size)
        assertFailsWith<IllegalArgumentException> { full.addRegion(HudRegionContent.ENGINE) }
    }

    @Test fun layoutsRoundTripAndResetWithoutChangingRuntimePreferences() {
        val original = AppSettings(hudEngineIndex = 2, hudClickThrough = false)
        val scene = HudSceneLayout.initial(original)
        val configured = original.copy(hudSceneLayout = scene)
        assertEquals(configured, SettingsJson.decode(SettingsJson.encode(configured)))
        assertNull(SettingsJson.decode(SettingsJson.encode(AppSettings())).hudSceneLayout)
        assertEquals(2, scene.regions.single { it.content == HudRegionContent.ENGINE }.engineIndex)
        assertNull(configured.withHudLayout(AppSettings()).hudSceneLayout)
        assertFalse(configured.withHudLayout(AppSettings()).hudClickThrough)
        assertEquals(scene, AppSettings().withHudLayout(configured).hudSceneLayout)
    }

    @Test fun rejectOffCanvasDuplicateAndInvalidAlphaConfigurations() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 10, 10, 200, 100)
        assertFailsWith<IllegalArgumentException> { HudSceneLayout(300, 200, listOf(region, region)) }
        assertFailsWith<IllegalArgumentException> { HudSceneLayout(300, 200, listOf(region.copy(x = 200))) }
        for (alpha in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { region.copy(backgroundAlpha = alpha) }
            assertFailsWith<IllegalArgumentException> { region.copy(contentAlpha = alpha) }
        }
        val layout = HudSceneLayout(300, 200, listOf(region))
        val json = SettingsJson.encode(AppSettings(hudSceneLayout = layout))
        assertFails { SettingsJson.decode(json.replace("\"FLIGHT\"", "\"unknown\"")) }
    }
}
