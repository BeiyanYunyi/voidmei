package voidmei.config

import kotlin.test.*

class LegacyEngineControlsTest {
    private val targets = linkedMapOf("disableEngineInfoThrottle" to "throttle",
        "disableEngineInfoPitch" to "rpm_control", "disableEngineInfoMixture" to "mixture",
        "disableEngineInfoRadiator" to "radiator", "disableEngineInfoCompressor" to "compressor")
    private fun read(type: String, value: String) = LegacySettingsReader.read("(panel p " +
        targets.keys.joinToString(" ") { "(item c :type $type :target $it :value $value)" } + ")")

    @Test fun switchesImportVisibilityWithoutConfusingPitchAngleOrChangingRegions() {
        val current = AppSettings(hudEngineIndex = 4, hudEngineFields = listOf("future", "pitch", "rpm_control", "throttle"),
            hudSceneLayout = HudSceneLayout.initial(AppSettings()))
        for (type in listOf("switch", "switch-inv")) for (value in listOf(false, true)) {
            val imported = read(type, value.toString())
            val visible = if (type == "switch-inv") value else !value
            assertEquals(targets.values.associateWith { visible }, imported.hudEngineFieldChoices)
            assertTrue(imported.unmigrated.isEmpty())
            assertTrue(imported.hasChanges)
            val expected = if (visible) listOf("future", "pitch", "rpm_control", "throttle", "mixture", "radiator", "compressor")
                else listOf("future", "pitch")
            val restored = imported.applyTo(current)
            assertEquals(current.copy(hudEngineFields = expected), restored)
            assertEquals(restored, SettingsJson.decode(SettingsJson.encode(restored)))
            assertEquals(restored, imported.applyTo(restored))
        }
        for (value in listOf("1", "null", "TRUE")) assertFails { read("switch", value) }
        assertFails { read("data", "true") }
    }

    @Test fun wholeAircraftControlsUseWholeAircraftFieldsAndMergeDuplicateVisibility() {
        val imported = LegacySettingsReader.read("""(panel p
            (item p :type switch-inv :target disableEngineInfoPower :value true)
            (item f :type switch-inv :target disableEngineInfoLFuel :value true))""")
        assertEquals(mapOf("power_percent" to true, "fuel_percent" to true), imported.hudFieldChoices)
        assertTrue(imported.hudEngineFieldChoices.isEmpty())
        val current = AppSettings(hudFields = listOf("future", "ias"))
        assertEquals(current.copy(hudFields = listOf("future", "ias", "power_percent", "fuel_percent")), imported.applyTo(current))
        val duplicate = LegacySettingsReader.read("""(panel p
            (item p :type switch-inv :target disableEngineInfoPower :value false)
            (item r :type data :target getPowerPercent :value true))""")
        assertEquals(true, duplicate.hudFieldChoices["power_percent"])
        val hidden = LegacySettingsReader.read("""(panel p
            (item p :type switch-inv :target disableEngineInfoPower :value false))""")
        assertEquals(false, hidden.hudFieldChoices["power_percent"])
        assertFalse("power_percent" in hidden.applyTo(imported.applyTo(current)).hudFields)
    }
}
