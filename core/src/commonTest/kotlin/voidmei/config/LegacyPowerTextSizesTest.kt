package voidmei.config

import kotlin.test.*

class LegacyPowerTextSizesTest {
    @Test fun allPowerOffsetsMigrateOnlySelectedRegionAndRoundTrip() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 20, 30, 300, 200, fontScale = 1.5f, readingLabelFont = "serif")
        val current = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two"))))
        for (offset in -6..18) {
            val parsed = LegacySettingsReader.read("""(panel "动力信息" (group style (item size :target fontSize :type slider :value $offset)))""")
            assertFalse(parsed.hasChanges)
            assertEquals(current, parsed.applyTo(current))
            val half = ((24 + offset + 1) / 2).toFloat()
            val expected = ReadingTextSizes(half, (24 + offset).toFloat(), half)
            assertEquals(expected, parsed.powerTextSizes)
            val result = parsed.copy(powerTextSizesRegionId = "two").applyTo(current)
            assertEquals(listOf(one, one.copy(id = "two", readingTextSizes = expected,
                readingTextWeights = ReadingTextWeights.legacyFlight, fontScale = 1f)), result.hudSceneLayout!!.regions)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        }
    }
    @Test fun attributeWinsAndOtherPanelSizesRemainUnmigrated() {
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :font-size 6 (item size :target fontSize :type slider :value 0))
            (panel "MiniHUD" (item size :target fontSize :type slider :value 20))""")
        assertEquals(ReadingTextSizes(15f, 30f, 15f), parsed.powerTextSizes)
        assertEquals(listOf("MiniHUD"), parsed.unmigrated.single().sourcePath)
        for (value in listOf("-7", "19", "1.5", "NaN")) {
            assertFails { LegacySettingsReader.read("""(panel "动力信息" :font-size $value)""") }
            assertFails { LegacySettingsReader.read("""(panel "动力信息" (item size :target fontSize :type slider :value $value))""") }
        }
        assertFails { LegacySettingsReader.read("""(panel "动力信息" (item size :target fontSize :type slider :value 0)
            (item size :target fontSize :type slider :value 2))""") }
    }
}
