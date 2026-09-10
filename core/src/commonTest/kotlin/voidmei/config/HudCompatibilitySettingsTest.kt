package voidmei.config

import kotlin.test.*

class HudCompatibilitySettingsTest {
    @Test fun platformDefaultOnlyAppliesWhenTheChoiceIsAbsent() {
        assertTrue(SettingsJson.decode("""{"version":1}""", true).hudCompatibilityMode)
        for (choice in listOf(false, true)) {
            assertEquals(choice, SettingsJson.decode("""{"version":1,"hudCompatibilityMode":$choice}""", true).hudCompatibilityMode)
        }
        assertFails { SettingsJson.decode("""{"version":1,"hudCompatibilityMode":null}""", true) }
    }

    @Test fun compatibilityIsOptInAndUsesStrictPersistedBoolean() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudCompatibilityMode)
        val enabled = AppSettings(hudCompatibilityMode = true)
        assertEquals(enabled, SettingsJson.decode(SettingsJson.encode(enabled)))
        for (invalid in listOf("\"true\"", "1", "null")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudCompatibilityMode":$invalid}""") }
        }
    }
}
