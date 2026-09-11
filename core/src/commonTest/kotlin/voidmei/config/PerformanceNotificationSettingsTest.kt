package voidmei.config

import kotlin.test.*

class PerformanceNotificationSettingsTest {
    @Test fun defaultsOffAndRoundTripsStrictBoolean() {
        assertFalse(SettingsJson.decode("""{"version":1}""").recordingPerformanceNotifications)
        val settings = AppSettings(recordingPerformanceNotifications = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (invalid in listOf("\"true\"", "1", "null", "{}")) {
            assertFails { SettingsJson.decode("""{"version":1,"recordingPerformanceNotifications":$invalid}""") }
        }
    }

    @Test fun legacySwitchImportsBothValuesWithoutChangingOtherPreferences() {
        for (enabled in listOf(true, false)) {
            val imported = LegacySettingsReader.read("""(panel "设置" (item "状态" :type switch :target "enableAltInformation" :value $enabled))""")
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            val current = AppSettings(recordingPerformanceNotifications = !enabled, voiceEnabled = true)
            assertEquals(current.copy(recordingPerformanceNotifications = enabled), imported.applyTo(current))
        }
    }
}
