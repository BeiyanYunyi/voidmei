package voidmei.config

import kotlin.test.*

class AttitudeRefreshTest {
    @Test fun persistsAndImportsDisplayIntervalWithoutChangingPolling() {
        assertEquals(0, SettingsJson.decode("""{"version":1}""").hudAttitudeRefreshMs)
        for (interval in listOf(10, 40, 100)) {
            val imported = LegacySettingsReader.read("""(panel p (item x :target attitudeIndicatorFreqMs :type slider :value $interval))""")
            val original = AppSettings(pollIntervalMs = 25, hudAttitude = false)
            val updated = imported.applyTo(original)
            assertEquals(original.copy(hudAttitudeRefreshMs = interval), updated)
            assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
            assertTrue(imported.hasChanges); assertTrue(imported.unmigrated.isEmpty())
            assertEquals(0, updated.withHudLayout(AppSettings()).hudAttitudeRefreshMs)
        }
    }
    @Test fun invalidIntervalsAndTypesAreRejected() {
        for (value in listOf("-1", "1", "9", "101", "40.5", "null", "true", "\"40\""))
            assertFails { SettingsJson.decode("""{"hudAttitudeRefreshMs":$value}""") }
        for (value in listOf("0", "9", "101", "40.5", "bad"))
            assertFails { LegacySettingsReader.read("""(panel p (item x :target attitudeIndicatorFreqMs :type slider :value $value))""") }
        assertFails { LegacySettingsReader.read("""(panel p (item x :target attitudeIndicatorFreqMs :type input :value 40))""") }
    }
}
