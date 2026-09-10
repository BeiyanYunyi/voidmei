package voidmei.config

import kotlin.test.*

class HudNumberFontTest {
    @Test fun settingsRoundTripAndRejectInvalidNames() {
        for (name in listOf(null, "Sarasa Mono SC", "等距更纱黑体 SC")) {
            val settings = AppSettings(hudNumberFont = name)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertNull(SettingsJson.decode("""{"version":1}""").hudNumberFont)
        for (name in listOf("", "  ", "x\ny", "a".repeat(201)))
            assertFails { AppSettings(hudNumberFont = name) }
        assertFails { SettingsJson.decode("""{"version":1,"hudNumberFont":123}""") }
    }

    @Test fun legacyFontImportsIndependentlyAndInvalidNamesPreserveCurrent() {
        fun read(value: String) = LegacySettingsReader.read("""(panel p
            (item font :type combo :target MonoNumFont :value "$value"))""")
        val current = AppSettings(hudNumberFont = "Existing", hudFontScale = 1.5f)
        val imported = read("Sarasa Mono SC")
        assertTrue(imported.hasChanges)
        assertEquals(current.copy(hudNumberFont = "Sarasa Mono SC"), imported.applyTo(current))
        assertTrue(imported.unmigrated.isEmpty())
        val invalid = read(" ")
        assertFalse(invalid.hasChanges)
        assertEquals(current, invalid.applyTo(current))
        assertEquals("MonoNumFont", invalid.unmigrated.single().target)
        assertFails { LegacySettingsReader.read("""(panel p (item f :type switch :target MonoNumFont :value true))""") }
    }
}
