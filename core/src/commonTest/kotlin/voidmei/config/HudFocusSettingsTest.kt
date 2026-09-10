package voidmei.config

import kotlin.test.*

class HudFocusSettingsTest {
    @Test fun autoHideIsOptInAndStrictlyPersisted() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudAutoHideOnFocusLoss)
        val settings = AppSettings(hudAutoHideOnFocusLoss = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (invalid in listOf("\"true\"", "1", "null")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudAutoHideOnFocusLoss":$invalid}""") }
        }
    }
}
