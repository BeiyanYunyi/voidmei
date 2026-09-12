package voidmei.config

import kotlin.test.*

class LegacyEnginePositionsTest {
    @Test fun explicitlySelectedWindowsUseRatioAndPixelConversionAndPreserveGeometry() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :x 500 :y .25)
            (panel "引擎控制" :x .95 :y .95)""")
        val one = HudRegion("one", HudRegionContent.ENGINE, 10, 20, 300, 200, fields = listOf("rpm"))
        val two = one.copy(id = "two", visible = false)
        val flight = one.copy(id = "flight", content = HudRegionContent.FLIGHT)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, two, flight)))
        assertFalse(parsed.hasChanges)
        assertEquals(original, parsed.applyTo(original))
        val selected = parsed.copy(enginePositionTargets = mapOf("engineInfoSwitch" to "one", "enableEngineControl" to "two"),
            legacyScreenSize = LegacyScreenSize(2000, 1200))
        val result = selected.applyTo(original)
        assertEquals(listOf(one.copy(x = 250, y = 150), two.copy(x = 700, y = 400), flight), result.hudSceneLayout!!.regions)
        assertEquals(original.copy(hudSceneLayout = result.hudSceneLayout), result)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(result, selected.applyTo(result))
        assertFails { selected.copy(legacyScreenSize = null).applyTo(original) }
        assertFails { selected.copy(enginePositionTargets = mapOf("engineInfoSwitch" to "one", "enableEngineControl" to "one")).applyTo(original) }
        assertEquals(original, selected.copy(enginePositionTargets = mapOf("engineInfoSwitch" to "flight", "enableEngineControl" to "missing")).applyTo(original))
    }
    @Test fun missingAxisDefaultsAndMalformedCoordinatesAreRejected() {
        assertEquals(LegacyHudPosition(.1, .3), LegacySettingsReader.read("""(panel "动力信息" :y .3)""").enginePanelPositions["engineInfoSwitch"])
        for (text in listOf("""(panel "动力信息" :x NaN)""", """(panel "引擎控制" :y nope)""",
            """(panel "动力信息" :x .2 :x .3)""", """(panel "动力信息" :x .2)(panel "动力信息" :y .3)"""))
            assertFails { LegacySettingsReader.read(text) }
    }
}
