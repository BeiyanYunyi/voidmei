package voidmei.config

import kotlin.test.*

class LegacyModelWindowsTest {
    private fun read(value: String, type: String = "switch") = LegacySettingsReader.read("""(panel p
        (item m :target enableFMPrint :type $type :value $value))""")
    @Test fun enableImportIsOptionalAndInitiallyHiddenWithExplicitShowChoice() {
        val parsed = read("true")
        val current = AppSettings(modelWindowEnabled = true, modelWindowPosition = WindowPosition(100f, 200f), hiddenModelSections = setOf(ModelDetailSection.RAW))
        assertFalse(parsed.hasChanges); assertTrue(parsed.unmigrated.isEmpty())
        assertEquals(current, parsed.applyTo(current))
        val selected = parsed.copy(importModelWindows = true)
        val expected = current.copy(modelWindowEnabled = false, modelJetWindowEnabled = true, modelJetWindowAutoClose = true)
        assertEquals(expected, selected.applyTo(current))
        assertEquals(expected, selected.applyTo(expected))
        assertEquals(expected.copy(modelWindowEnabled = true), selected.copy(showImportedModelWindows = true).applyTo(current))
        assertFalse(selected.applyTo(current).modelWindowHotkeyEnabled)
    }
    @Test fun disableClosesBothWindowsAndListenerWithoutErasingPreferences() {
        val current = AppSettings(modelWindowEnabled = true, modelJetWindowEnabled = true, modelWindowHotkeyEnabled = true,
            modelWindowHotkey = "P", modelJetWindowAutoClose = true, modelJetWindowPosition = WindowPosition(200f, 100f))
        val expected = current.copy(modelWindowEnabled = false, modelJetWindowEnabled = false, modelWindowHotkeyEnabled = false)
        assertEquals(expected, read("false").copy(importModelWindows = true, showImportedModelWindows = true).applyTo(current))
        val combined = read("false").copy(importModelWindows = true, importModelHotkey = true, modelHotkey = "F1")
        assertEquals(expected.copy(modelWindowHotkey = "F1"), combined.applyTo(current))
        assertEquals(expected, SettingsJson.decode(SettingsJson.encode(expected)))
    }
    @Test fun supportsInvertedSwitchAndRejectsInvalidOrDuplicateRows() {
        assertFalse(read("true", "switch-inv").modelWindows!!)
        for (value in listOf("TRUE", "1", "null")) assertFails { read(value) }
        assertFails { read("true", "data") }
        assertFails { LegacySettingsReader.read("""(panel p
            (item a :target enableFMPrint :type switch :value true)
            (item b :target enableFMPrint :type switch :value false))""") }
    }
}
