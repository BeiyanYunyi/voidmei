package voidmei.config

import kotlin.test.*

class LegacyRendererTest {
    private fun read(value: String, type: String = "switch") = LegacySettingsReader.read(
        """(panel settings (item renderer :type $type :target gpuCompatibilityMode :value $value))""")

    @Test fun explicitLegacyPreferenceImportsBothDirectionsAndPreservesIndependentSettings() {
        for (enabled in listOf(false, true)) for (compatible in listOf(false, true)) {
            val imported = read(enabled.toString())
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(enabled, imported.softwareRendering)
            val current = AppSettings(softwareRendering = !enabled, hudCompatibilityMode = compatible,
                hudEnabled = true, hudClickThrough = true, hudOpacity = .3f)
            val result = imported.applyTo(current)
            assertEquals(current.copy(softwareRendering = enabled), result)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        }
    }

    @Test fun absentPreferenceIsPreservedAndInvalidOrDuplicateValuesAreRejected() {
        val other = LegacySettingsReader.read("""(panel p (item a :type switch :target enableVoiceWarn :value true))""")
        for (enabled in listOf(false, true))
            assertEquals(enabled, other.applyTo(AppSettings(softwareRendering = enabled)).softwareRendering)
        for (invalid in listOf("0", "1", "null", "TRUE", "invalid")) assertFails { read(invalid) }
        assertFails { read("true", "input") }
        assertFails { LegacySettingsReader.read("""(panel p
            (item a :type switch :target gpuCompatibilityMode :value true)
            (item b :type switch :target gpuCompatibilityMode :value false))""") }
        assertFalse(read("true", "switch-inv").softwareRendering!!)
    }
}
