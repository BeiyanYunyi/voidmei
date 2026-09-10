package voidmei.config

import kotlin.test.*

class HudClickThroughSettingsTest {
    @Test fun oldProfilesRemainInteractiveAndExplicitChoiceRoundTrips() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudClickThrough)
        for (choice in listOf(false, true)) {
            val settings = AppSettings(hudClickThrough = choice)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (invalid in listOf("\"true\"", "1", "null")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudClickThrough":$invalid}""") }
        }
    }
}
