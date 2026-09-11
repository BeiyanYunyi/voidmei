package voidmei.config

import kotlin.test.*

class SettingsBackupTest {
    @Test fun transferRequiresSettingsIdentityWhileOrdinaryLoadingRetainsDefaults() {
        assertEquals(AppSettings(), SettingsJson.decode("""{"version":1}"""))
        for (text in listOf("""{"version":1}""", """{"version":1,"presets":{}}""",
            """{"version":1,"endpoint":"http://localhost:8111"}""")) {
            assertFails { SettingsJson.decodeBackup(text) }
        }
        val settings = AppSettings(hudCompatibilityMode = true, hudFields = listOf("sep", "unknown"))
        assertEquals(settings, SettingsJson.decodeBackup(SettingsJson.encode(settings)))
    }
}
