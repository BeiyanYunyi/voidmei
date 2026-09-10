package voidmei.config

import kotlin.test.*

class TextFontSettingsTest {
    @Test fun textFontRoundTripsAndOldSettingsKeepTheDefault() {
        assertNull(SettingsJson.decode("""{"version":1}""").textFont)
        val settings = AppSettings(textFont = "Sarasa Mono SC", hudNumberFont = "monospace")
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (invalid in listOf("", " ", "x\ny", "x".repeat(201)))
            assertFailsWith<IllegalArgumentException> { AppSettings(textFont = invalid) }
        assertFailsWith<IllegalArgumentException> { SettingsJson.decode("""{"version":1,"textFont":42}""") }
    }

    @Test fun legacyTextFontImportsIndependentlyOfHudNumbers() {
        val imported = LegacySettingsReader.read("""(panel p (item font :type combo :target GlobalTextFont :value "serif"))""")
        val current = AppSettings(hudNumberFont = "monospace")
        assertEquals(current.copy(textFont = "serif"), imported.applyTo(current))
        assertTrue(imported.unmigrated.isEmpty())
        assertTrue(imported.hasChanges)
    }
}
