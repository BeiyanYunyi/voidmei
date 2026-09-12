package voidmei.config

import kotlin.test.*

class LegacyEngineFontTest {
    @Test fun panelFontsTakePrecedenceAndOnlySelectedEngineLabelsChange() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :font serif
            (group style (item f :type combo :target fontName :value monospace)))
            (panel "引擎控制" :font sans-serif)
            (panel "其他" (item f :type combo :target fontName :value other))""")
        assertEquals(mapOf("engineInfoSwitch" to "serif", "enableEngineControl" to "sans-serif"), parsed.enginePanelFonts)
        assertEquals(listOf("其他"), parsed.unmigrated.single().sourcePath)
        assertFalse(parsed.hasChanges)
        val one = HudRegion("one", HudRegionContent.ENGINE, 10, 20, 300, 200, readingLabelFont = "old", readingNumberFont = "mono")
        val two = one.copy(id = "two")
        val flight = one.copy(id = "flight", content = HudRegionContent.FLIGHT)
        val current = AppSettings(textFont = "global", hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, two, flight)))
        assertEquals(current, parsed.applyTo(current))
        val selected = parsed.copy(engineFontTargets = mapOf("engineInfoSwitch" to "one", "enableEngineControl" to "two"))
        val result = selected.applyTo(current)
        assertEquals(current.copy(hudSceneLayout = current.hudSceneLayout!!.copy(regions =
            listOf(one.copy(readingLabelFont = "serif"), two.copy(readingLabelFont = "sans-serif"), flight))), result)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(result, selected.applyTo(result))
        assertEquals(current, parsed.copy(engineFontTargets = mapOf("engineInfoSwitch" to "flight", "enableEngineControl" to "missing")).applyTo(current))
        assertFails { parsed.copy(engineFontTargets = mapOf("engineInfoSwitch" to "one", "enableEngineControl" to "one")).applyTo(current) }
    }
    @Test fun scopedHistoricalFontCanApplyToNewRegionAndInvalidNamesFail() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" (group style (item f :type combo :target fontName :value serif)))""")
        val base = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)))
        val created = base.legacyEngineRegion("engineInfoSwitch")
        assertEquals("serif", parsed.copy(engineRegionsToCreate = listOf(created), engineFontTargets = mapOf("engineInfoSwitch" to created.id))
            .applyToScene(base)!!.regions.last().readingLabelFont)
        for (body in listOf(":font serif :font monospace", ":font \"${"a".repeat(201)}\"",
            "(item f :type combo :target fontName :value \"\")", "(item f :type switch :target fontName :value true)",
            "(item f :type combo :target fontName :value serif)(item f :type combo :target fontName :value serif)"))
            assertFails { LegacySettingsReader.read("(panel \"动力信息\" $body)") }
    }
}
