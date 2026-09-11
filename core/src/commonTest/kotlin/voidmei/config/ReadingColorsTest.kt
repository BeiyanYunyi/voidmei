package voidmei.config

import kotlin.test.*

class ReadingColorsTest {
    @Test fun strictPaletteRoundTripsAndInvalidRolesAndValuesAreRejected() {
        val settings = AppSettings(readingColors = mapOf("value" to "#12AB34", "shade" to "#00000080"))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertTrue(SettingsJson.decode("""{"version":1}""").readingColors.isEmpty())
        for (value in listOf("null", "[]", "\"red\"", "{\"value\":123}", "{\"value\":\"red\"}", "{\"unknown\":\"#000000\"}"))
            assertFails { SettingsJson.decode("""{"version":1,"readingColors":$value}""") }
    }
}
