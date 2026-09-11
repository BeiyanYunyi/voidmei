package voidmei.config

import kotlin.test.*

class HudPresetFileTest {
    private val scene = HudSceneLayout(400, 240, listOf(HudRegion("engine", HudRegionContent.ENGINE,
        0, 0, 400, 240, .25f, .75f, engineIndex = 2, fields = listOf("rpm", "future"),
        showEngineInstruments = false)), enabled = false, displayId = "monitor")

    @Test fun backupRetainsIndependentLayoutAndUnknownFields() {
        val presets = mapOf("巡航" to scene, "作战" to scene.copy(enabled = true))
        assertEquals(presets, HudPresetFile.decode(HudPresetFile.encode(presets)))
        assertEquals(emptyMap(), HudPresetFile.decode(HudPresetFile.encode(emptyMap())))
    }

    @Test fun wrongFormatsVersionsAndInvalidNamesAreRejected() {
        val valid = HudPresetFile.encode(mapOf("巡航" to scene))
        for (text in listOf("{}", SettingsJson.encode(AppSettings()),
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"巡航\"", "\" 巡航\""),
            valid.replace("\"width\":400", "\"width\":-1"))) {
            assertFails { HudPresetFile.decode(text) }
        }
        assertFails { HudPresetFile.encode((1..17).associate { "$it" to scene }) }
    }
}
