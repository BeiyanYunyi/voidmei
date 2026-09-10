package voidmei.config

import kotlin.test.*

class RecordingStartupSettingsTest {
    @Test fun autoRecordingDefaultsOffAndRoundTripsStrictBoolean() {
        assertFalse(SettingsJson.decode("""{"version":1}""").recordingAutoStart)
        val settings = AppSettings(recordingAutoStart = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFailsWith<IllegalArgumentException> { SettingsJson.decode("""{"version":1,"recordingAutoStart":"true"}""") }
    }
}
