package voidmei.config

import kotlin.test.*

class HudSceneLayoutTest {
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
