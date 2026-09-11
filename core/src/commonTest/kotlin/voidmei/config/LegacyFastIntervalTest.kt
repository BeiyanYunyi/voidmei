package voidmei.config

import kotlin.test.*

class LegacyFastIntervalTest {
    @Test fun validOldFastPollingImportsAndPersistsAlongsideOtherSettings() {
        for (key in listOf("dataPollIntervalMs", "Interval")) for (interval in listOf(10, 19)) {
            val item = """(item rate :target "$key" :type slider :value $interval)"""
            val current = AppSettings(pollIntervalMs = 80)
            val mixed = LegacySettingsReader.read("""(panel p $item (item volume :target voiceVolume :type slider :value 75))""")
            assertEquals(current.copy(pollIntervalMs = interval.toLong(), voiceVolume = 75), mixed.applyTo(current))
            assertEquals(interval.toLong(), mixed.intervalMs)
            assertTrue(mixed.hasChanges)
            assertTrue(mixed.unmigrated.isEmpty())
            val only = LegacySettingsReader.read("(panel p $item)")
            assertTrue(only.hasChanges)
            assertEquals(current.copy(pollIntervalMs = interval.toLong()), only.applyTo(current))
            assertEquals(only.applyTo(current), SettingsJson.decode(SettingsJson.encode(only.applyTo(current))))
            assertTrue(only.unmigrated.isEmpty())
        }
    }
}
