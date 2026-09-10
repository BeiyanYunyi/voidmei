package voidmei.config

import kotlin.test.*

class NumberFontSettingsTest {
    @Test fun independentFontsRoundTripAndOldSettingsRetainDefaults() {
        assertNull(SettingsJson.decode("""{"version":1}""").numberFont)
        val settings = AppSettings(textFont = "sans-serif", numberFont = "serif", hudNumberFont = "monospace")
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (invalid in listOf("", " ", "x\ny", "x".repeat(201)))
            assertFailsWith<IllegalArgumentException> { AppSettings(numberFont = invalid) }
        assertFailsWith<IllegalArgumentException> { SettingsJson.decode("""{"version":1,"numberFont":42}""") }
    }

    @Test fun invalidGlobalFontDoesNotPreventDedicatedFontMigration() {
        val imported = LegacySettingsReader.read("""(panel p
            (item global :type combo :target GlobalNumFont :value "")
            (item dedicated :type combo :target MonoNumFont :value "monospace"))""")
        val current = AppSettings(numberFont = "serif", textFont = "sans-serif")
        assertEquals(current.copy(hudNumberFont = "monospace"), imported.applyTo(current))
        assertTrue(imported.unmigrated.any { it.target == "GlobalNumFont" })
    }
}
