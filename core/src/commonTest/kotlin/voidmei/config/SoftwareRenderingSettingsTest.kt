package voidmei.config

import kotlin.test.*

class SoftwareRenderingSettingsTest {
    @Test fun optInPersistsAndRejectsInvalidTypes() {
        assertFalse(SettingsJson.decode("""{"version":1}""").softwareRendering)
        for (enabled in listOf(false, true)) {
            val settings = AppSettings(softwareRendering = enabled, hudCompatibilityMode = !enabled)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (value in listOf("\"true\"", "1", "null", "[]", "{}")) {
            assertFails { SettingsJson.decode("""{"version":1,"softwareRendering":$value}""") }
        }
    }
}
