package voidmei.config

import kotlin.test.*

class HudColumnsTest {
    @Test fun columnsDefaultToAutomaticAndPersistStrictly() {
        assertEquals(0, SettingsJson.decode("""{"version":1}""").hudReadingColumns)
        for (columns in 0..2) {
            val settings = AppSettings(hudReadingColumns = columns)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (value in listOf("-1", "3", "1.5", "\"2\"", "null", "true")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudReadingColumns":$value}""") }
        }
    }
}
