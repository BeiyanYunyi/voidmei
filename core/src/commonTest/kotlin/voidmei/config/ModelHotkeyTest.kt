package voidmei.config

import kotlin.test.*

class ModelHotkeyTest {
    @Test fun keysModifiersPersistAndConflictsAreRejected() {
        for (key in ModelHotkeyKey.entries) for (prefix in listOf("", "Ctrl+", "Shift+", "Alt+", "Ctrl+Shift+Alt+")) {
            val value = prefix + key.name
            val settings = AppSettings(modelWindowHotkey = value)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (value in listOf("Ctrl+Shift+H", "Shift+Ctrl+M", "Ctrl+Ctrl+M", "Meta+P", "F13", "", "p"))
            assertFails { AppSettings(modelWindowHotkey = value) }
    }
    @Test fun legacyImportRequiresSelectionAndNeverEnablesListener() {
        fun read(value: Int) = LegacySettingsReader.read("""(panel p (item k :target displayFmKey :type hotkey :value $value))""")
        val current = AppSettings()
        val p = read(25)
        assertEquals("P", p.modelHotkey); assertFalse(p.hasChanges); assertTrue(p.unmigrated.isEmpty())
        assertEquals(current, p.applyTo(current))
        assertEquals(current.copy(modelWindowHotkey = "P"), p.copy(importModelHotkey = true).applyTo(current))
        val disabled = read(0).copy(importModelHotkey = true).applyTo(current.copy(modelWindowHotkeyEnabled = true))
        assertFalse(disabled.modelWindowHotkeyEnabled); assertEquals(current.modelWindowHotkey, disabled.modelWindowHotkey)
        assertNull(read(65535).modelHotkey); assertEquals("displayFmKey", read(65535).unmigrated.single().target)
    }
}
