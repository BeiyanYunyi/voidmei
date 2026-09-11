package voidmei.config

import kotlin.test.*

class LegacyReadingColorTest {
    private fun read(key: String, value: String) = LegacySettingsReader.read("""(panel p
        (item color :type color :target $key :value "$value"))""")

    @Test fun allFiveColorsMapToHudWithoutChangingOtherPreferences() {
        val imported = LegacySettingsReader.read("""(panel p
            (item a :type color :target fontLabel :value "#12ab3480")
            (item b :type color :target fontNum :value "255, 255, 255")
            (item c :type color :target fontUnit :value "232, 147, 50, 255")
            (item d :type color :target fontWarn :value "#FF2400FF")
            (item e :type color :target fontShade :value "0, 0, 0, 128"))""")
        val current = AppSettings(hudOpacity = .4f, hudCrosshairImage = "/keep.png")
        val result = imported.applyTo(current)
        assertEquals(current.copy(hudLabelColor = "#12AB3480", hudValueColor = "#FFFFFFFF",
            hudUnitColor = "#E89332FF", hudWarningColor = "#FF2400FF", hudShadeColor = "#00000080", readingColors = mapOf("label" to "#12AB3480", "value" to "#FFFFFFFF", "unit" to "#E89332FF", "warning" to "#FF2400FF", "shade" to "#00000080")), result)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(5, imported.unmigrated.size)
        assertTrue(imported.unmigrated.all { it.label == "表格以外的全局配色" })
    }

    @Test fun invalidColorPreservesCurrentAndDecimalClampsLikeLegacy() {
        val current = AppSettings(hudValueColor = "#123456")
        for (bad in listOf("wrong", "#FFF", "0, 1", "1, 2, nope", "1,2,3,4,5")) {
            val imported = read("fontNum", bad)
            assertFalse(imported.hasChanges)
            assertEquals(current, imported.applyTo(current))
            assertTrue(imported.unmigrated.single().label.contains("颜色无效"))
        }
        assertEquals(current.copy(hudShadeColor = "#00FF0080", readingColors = mapOf("shade" to "#00FF0080")), read("fontShade", "-1, 300, 0, 128").applyTo(current))
        assertFails { LegacySettingsReader.read("""(panel p (item a :type switch :target fontNum :value true))""") }
    }
}
