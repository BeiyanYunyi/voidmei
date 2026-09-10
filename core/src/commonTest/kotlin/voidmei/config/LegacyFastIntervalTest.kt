package voidmei.config

import kotlin.test.*

class LegacyFastIntervalTest {
    @Test fun validOldFastPollingDoesNotBlockOtherSettingsOrChangeCurrentInterval() {
        for (key in listOf("dataPollIntervalMs", "Interval")) for (interval in listOf(10, 19)) {
            val item = """(item rate :target "$key" :type slider :value $interval)"""
            val current = AppSettings(pollIntervalMs = 80)
            val mixed = LegacySettingsReader.read("""(panel p $item (item volume :target voiceVolume :type slider :value 75))""")
            assertEquals(current.copy(voiceVolume = 75), mixed.applyTo(current))
            assertNull(mixed.intervalMs)
            assertTrue(mixed.hasChanges)
            assertEquals(key, mixed.unmigrated.single().target)
            assertTrue(mixed.unmigrated.single().label.contains("$interval ms"))
            val only = LegacySettingsReader.read("(panel p $item)")
            assertFalse(only.hasChanges)
            assertEquals(current, only.applyTo(current))
            assertEquals(1, only.unmigrated.size)
        }
    }
}
