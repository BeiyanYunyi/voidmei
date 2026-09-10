package voidmei.config

import kotlin.test.*

class LegacyPortTest {
    private fun source(value: String, type: String = "input") =
        """(panel "设置" (item "8111端口" :type $type :target "httpPort" :value $value))"""

    @Test fun importsPortWithoutReplacingHostOrOtherSettings() {
        val imported = LegacySettingsReader.read(source("9222"))
        assertTrue(imported.hasChanges)
        assertTrue(imported.unmigrated.isEmpty())
        for ((before, after) in listOf(
            "http://127.0.0.1:8111" to "http://127.0.0.1:9222",
            "https://telemetry.example/base/" to "https://telemetry.example:9222/base/",
            "http://[::1]:8111" to "http://[::1]:9222",
        )) {
            val current = AppSettings(endpoint = before, voiceVolume = 37)
            val updated = imported.applyTo(current)
            assertEquals(current.copy(endpoint = after), updated)
            assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
        }
    }

    @Test fun invalidPortsAndTypesAreRejectedAndAbsentPortIsPreserved() {
        for (value in listOf("0", "65536", "-1", "8111.5", "bad"))
            assertFailsWith<IllegalArgumentException> { LegacySettingsReader.read(source(value)) }
        assertFailsWith<IllegalArgumentException> { LegacySettingsReader.read(source("8111", "switch")) }
        val unrelated = LegacySettingsReader.read("""(panel "设置" (item "音量" :type slider :target "voiceVolume" :value 30))""")
        assertEquals("http://remote:9999", unrelated.applyTo(AppSettings(endpoint = "http://remote:9999")).endpoint)
        assertFailsWith<IllegalArgumentException> {
            LegacySettingsReader.read(source("9222")).applyTo(AppSettings(endpoint = "broken"))
        }
    }
}
