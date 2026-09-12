package voidmei.config

import kotlin.test.*

class LegacyEngineControlStyleTest {
    @Test fun allOffsetsUseJavaBaseAndOnlyModifyExplicitEngineTarget() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 20, 30, 300, 200, fontScale = 1.5f,
            readingLabelFont = "serif", readingNumberFont = "monospace")
        val current = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two"))))
        for (offset in -6..20) {
            val parsed = LegacySettingsReader.read("""(panel "引擎控制" (group style (item s :target fontSize :type slider :value $offset)))""")
            assertFalse(parsed.hasChanges)
            assertEquals(current, parsed.applyTo(current))
            val base = 24 + offset
            val half = ((base + 1) / 2).toFloat()
            val after = parsed.copy(engineControlStyleRegionId = "two").applyTo(current)
            assertEquals(listOf(one, one.copy(id = "two", engineControlDimensions = EngineControlDimensions(base * 4, base / 2),
                readingTextSizes = ReadingTextSizes(half, half, half), readingTextWeights = ReadingTextWeights(700, 700, 700), fontScale = 1f)),
                after.hudSceneLayout!!.regions)
            assertEquals(after, SettingsJson.decode(SettingsJson.encode(after)))
            assertEquals(current, parsed.copy(engineControlStyleRegionId = "missing").applyTo(current))
        }
    }
    @Test fun attributeWinsCreationWorksAndAmbiguousTargetsAreRejected() {
        val parsed = LegacySettingsReader.read("""(panel "引擎控制" :font-size 3 (item s :target fontSize :type slider :value 0))
            (panel "动力信息" :font-size 6)""")
        assertEquals(LegacyEngineControlStyle(3), parsed.engineControlStyle)
        val base = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)))
        val created = base.legacyEngineRegion("enableEngineControl")
        val selected = parsed.copy(engineRegionsToCreate = listOf(created), engineControlStyleRegionId = created.id)
        assertEquals(EngineControlDimensions(108, 13), selected.applyToScene(base)!!.regions.last().engineControlDimensions)
        assertFails { selected.copy(powerTextSizesRegionId = created.id).applyToScene(base) }
        assertEquals(base, parsed.copy(engineControlStyleRegionId = "flight").applyToScene(base))
        for (value in listOf("-7", "21", "1.5", "false")) {
            assertFails { LegacySettingsReader.read("""(panel "引擎控制" :font-size $value)""") }
            assertFails { LegacySettingsReader.read("""(panel "引擎控制" (item s :target fontSize :type slider :value $value))""") }
        }
        assertFails { LegacySettingsReader.read("""(panel "引擎控制" (item s :target fontSize :type slider :value 0)
            (item s :target fontSize :type slider :value 2))""") }
    }
}
