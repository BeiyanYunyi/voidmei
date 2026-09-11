package voidmei.config

import kotlin.test.*

class HudScenePresetsTest {
    @Test fun namedLayoutsRoundTripAndValidateNamesAndCount() {
        val scene = HudSceneLayout.initial(AppSettings()).copy(enabled = false)
        val settings = AppSettings(hudScenePresets = mapOf("巡航" to scene, "战斗" to scene.resizeCanvas(600, 400)))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertTrue(SettingsJson.decode("""{"version":1}""").hudScenePresets.isEmpty())
        for (name in listOf("", " ", " x", "x\ny", "a".repeat(81)))
            assertFailsWith<IllegalArgumentException> { AppSettings(hudScenePresets = mapOf(name to scene)) }
        assertFailsWith<IllegalArgumentException> { AppSettings(hudScenePresets = (1..17).associate { "$it" to scene }) }
    }
}
