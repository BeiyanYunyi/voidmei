package voidmei.config

import kotlin.test.*

class LegacyEngineVisibilityTest {
    @Test fun bothWindowsRequireExplicitDistinctTargetsAndOnlyChangeVisibility() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" (item x :target engineInfoSwitch :type switch :value false))
            (panel "引擎控制" (item x :target enableEngineControl :type switch :value true))""")
        assertFalse(parsed.hasChanges)
        assertTrue(parsed.unmigrated.isEmpty())
        val one = HudRegion("power", HudRegionContent.ENGINE, 12, 23, 300, 200, fields = listOf("power"))
        val two = one.copy(id = "controls", visible = false, fields = listOf("throttle"))
        val flight = one.copy(id = "flight", content = HudRegionContent.FLIGHT)
        val original = AppSettings(hudEnabled = false, hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, two, flight)))
        assertEquals(original, parsed.applyTo(original))
        val selected = parsed.copy(enginePanelTargets = mapOf("engineInfoSwitch" to "power", "enableEngineControl" to "controls"))
        val result = selected.applyTo(original)
        assertEquals(original.copy(hudSceneLayout = original.hudSceneLayout!!.copy(regions =
            listOf(one.copy(visible = false), two.copy(visible = true), flight))), result)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(result, selected.applyTo(result))
        assertEquals(original, parsed.copy(enginePanelTargets = mapOf("engineInfoSwitch" to "flight", "enableEngineControl" to "missing")).applyTo(original))
        assertFails { parsed.copy(enginePanelTargets = mapOf("engineInfoSwitch" to "power", "enableEngineControl" to "power")).applyTo(original) }
    }
    @Test fun panelSwitchesAndExplicitRowsUseExistingLegacyPrecedenceAndValidation() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :switch-key engineInfoSwitch :visible false
            (item x :target engineInfoSwitch :type switch :value true))
            (panel "引擎控制" :switch-key enableEngineControl :visible false)""")
        assertEquals(mapOf("engineInfoSwitch" to true, "enableEngineControl" to false), parsed.enginePanelVisibility)
        assertTrue(parsed.unmigrated.isEmpty())
        for (key in listOf("engineInfoSwitch", "enableEngineControl")) {
            assertFails { LegacySettingsReader.read("""(panel p (item x :target $key :type switch :value maybe))""") }
            assertFails { LegacySettingsReader.read("""(panel p :switch-key $key :visible maybe)""") }
        }
    }
}
