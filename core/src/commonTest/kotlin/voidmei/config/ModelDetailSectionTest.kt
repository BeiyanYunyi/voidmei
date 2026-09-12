package voidmei.config

import kotlin.test.*

class ModelDetailSectionTest {
    @Test fun displaySelectionRoundTripsAndOldSettingsShowEverything() {
        for (sections in listOf(emptySet(), setOf(ModelDetailSection.WEIGHT, ModelDetailSection.RAW), ModelDetailSection.entries.toSet())) {
            val settings = AppSettings(hiddenModelSections = sections)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(emptySet(), SettingsJson.decode("""{"version":1}""").hiddenModelSections)
        for (value in listOf("true", "[1]", "[\"UNKNOWN\"]", "null")) {
            assertFails { SettingsJson.decode("""{"version":1,"hiddenModelSections":$value}""") }
        }
    }
}
