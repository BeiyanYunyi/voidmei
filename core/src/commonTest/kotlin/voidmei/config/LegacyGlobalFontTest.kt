package voidmei.config

import kotlin.test.*

class LegacyGlobalFontTest {
    private fun item(key: String, value: String) = """(item font :type combo :target "$key" :value "$value")"""
    private fun read(vararg items: String) = LegacySettingsReader.read("(panel p ${items.joinToString(" ")})")

    @Test fun hudFontInheritsGlobalOnlyWhenDedicatedFontIsAbsentOrBlank() {
        val global = item("GlobalNumFont", "Sarasa Mono SC")
        for (imported in listOf(read(global), read(item("MonoNumFont", ""), global))) {
            assertEquals("Sarasa Mono SC", imported.hudNumberFont)
            assertEquals("Sarasa Mono SC", imported.applyTo(AppSettings(hudNumberFont = "previous")).hudNumberFont)
            assertEquals("Sarasa Mono SC", imported.numberFont)
            assertTrue(imported.unmigrated.isEmpty())
        }
        val dedicated = read(global, item("MonoNumFont", "dedicated"))
        assertEquals("dedicated", dedicated.hudNumberFont)
        assertEquals("Sarasa Mono SC", dedicated.numberFont)
        assertEquals("dedicated", dedicated.applyTo(AppSettings()).hudNumberFont)
    }

    @Test fun missingAndInvalidFontsPreserveCurrentChoice() {
        val current = AppSettings(hudNumberFont = "previous")
        for (imported in listOf(read(item("MonoNumFont", "")), read(item("GlobalNumFont", "")),
            read(item("GlobalNumFont", "x".repeat(201))))) {
            assertNull(imported.hudNumberFont)
            assertEquals(current, imported.applyTo(current))
            assertTrue(imported.unmigrated.isNotEmpty())
        }
        assertFailsWith<IllegalArgumentException> {
            LegacySettingsReader.read("""(panel p (item font :type input :target GlobalNumFont :value "name"))""")
        }
    }
}
