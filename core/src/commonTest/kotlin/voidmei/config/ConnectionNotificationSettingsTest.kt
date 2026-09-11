package voidmei.config

import kotlin.test.*

class ConnectionNotificationSettingsTest {
    @Test fun defaultsOffAndRoundTripsStrictBoolean() {
        assertFalse(SettingsJson.decode("""{"version":1}""").connectionNotifications)
        val settings = AppSettings(connectionNotifications = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (invalid in listOf("\"true\"", "1", "null", "{}")) {
            assertFails { SettingsJson.decode("""{"version":1,"connectionNotifications":$invalid}""") }
        }
    }

    @Test fun legacySwitchImportsBothValuesWithoutChangingOtherPreferences() {
        for (enabled in listOf(true, false)) {
            val imported = LegacySettingsReader.read("""(panel "设置" (item "状态" :type switch :target "enableStatusBar" :value $enabled))""")
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            val current = AppSettings(connectionNotifications = !enabled, voiceEnabled = true)
            assertEquals(current.copy(connectionNotifications = enabled), imported.applyTo(current))
        }
    }
}
