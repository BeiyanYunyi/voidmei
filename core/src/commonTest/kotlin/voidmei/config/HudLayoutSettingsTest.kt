package voidmei.config

import kotlin.test.*

class HudLayoutSettingsTest {
    @Test fun resetAndUndoRestoreRegionOptionsWithoutRollingBackSavedPresets() {
        val scene = HudSceneLayout(600, 300, listOf(
            HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 300, 300,
                showFlightStatus = false, showFlightInstruments = false),
            HudRegion("messages", HudRegionContent.MESSAGES, 300, 0, 300, 300,
                messageLimit = 20, fields = listOf("damage", "future")),
        ))
        val original = AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("原布局" to scene))
        val reset = original.withHudLayout(AppSettings())
        assertNull(reset.hudSceneLayout)
        assertEquals(original.hudScenePresets, reset.hudScenePresets)
        val edited = reset.copy(hudScenePresets = mapOf("改名后" to scene.copy(enabled = false)), voiceVolume = 25)
        val restored = edited.withHudLayout(original)
        assertEquals(scene, restored.hudSceneLayout)
        assertEquals(edited.hudScenePresets, restored.hudScenePresets)
        assertEquals(25, restored.voiceVolume)
        assertEquals(restored, SettingsJson.decode(SettingsJson.encode(restored)))
    }

    @Test fun restoringLayoutKeepsLaterConnectionRecordingAndWindowChanges() {
        val original = AppSettings(hudFields = listOf("sep", "future-field"), hudEngineIndex = 2,
            hudEngineFields = listOf("oil_temperature", "future-engine-field"), hudWidthDp = 680,
            hudFontScale = 1.5f, hudReadingColumns = 1, hudHiddenLabels = listOf("sep"),
            hudValueColor = "#12AB34", hudNumberFont = "Monospaced", hudCrosshair = true,
            hudCrosshairImage = "/example/crosshair.png")
        val later = original.withHudLayout(AppSettings()).copy(endpoint = "http://127.0.0.1:8112",
            hudEnabled = true, recordingAutoStart = true, hudPosition = WindowPosition(20f, 30f),
            hudCompatibilityMode = true, hudClickThrough = true, hudOpacity = .5f)
        assertEquals(AppSettings().hudFields, later.hudFields)
        val restored = later.withHudLayout(original)
        assertEquals(original.copy(endpoint = later.endpoint, hudEnabled = true, recordingAutoStart = true,
            hudPosition = later.hudPosition, hudCompatibilityMode = true, hudClickThrough = true, hudOpacity = .5f), restored)
        assertEquals(restored, SettingsJson.decode(SettingsJson.encode(restored)))
    }
}
