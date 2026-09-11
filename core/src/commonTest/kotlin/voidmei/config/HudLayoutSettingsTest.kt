package voidmei.config

import kotlin.test.*

class HudLayoutSettingsTest {
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
