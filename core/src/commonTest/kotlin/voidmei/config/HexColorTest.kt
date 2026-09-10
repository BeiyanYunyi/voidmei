package voidmei.config

import kotlin.test.*

class HexColorTest {
    @Test fun rgbAndRgbaUseTrailingAlphaAndRoundTrip() {
        assertEquals(0xFF12AB34L, parseHexColor("#12ab34"))
        assertEquals(0x8012AB34L, parseHexColor("#12ab3480"))
        assertEquals(0x0012AB34L, parseHexColor("#12ab3400"))
        assertEquals(null, SettingsJson.decode("""{"version":1}""").hudValueColor)
        val custom = AppSettings(hudLabelColor = "#00FF00", hudValueColor = "#12ab3480", hudWarningColor = "#FF0000", hudShadeColor = "#00000080", hudUnitColor = "#FF8800")
        assertEquals(custom, SettingsJson.decode(SettingsJson.encode(custom)))
        assertEquals(AppSettings(), SettingsJson.decode(SettingsJson.encode(AppSettings())))
    }
    @Test fun invalidColorsAreRejectedWithoutCoercion() {
        for (text in listOf("", "red", "#FFF", "#GG0000", "#000000000", " #000000", "#000000\n")) {
            assertNull(parseHexColor(text))
            assertFails { AppSettings(hudValueColor = text) }
            assertFails { AppSettings(hudShadeColor = text) }
            assertFails { AppSettings(hudUnitColor = text) }
        }
        assertFails { SettingsJson.decode("""{"hudValueColor":123456}""") }
        assertFails { SettingsJson.decode("""{"hudLabelColor":"#nope"}""") }
    }
}
