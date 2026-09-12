package voidmei.config

import kotlin.test.*

class LegacyScopedFontSizeTest {
    @Test fun nestedFlightRowIsScopedAndRemainsOptional() {
        for (offset in -6..20) {
            val imported = LegacySettingsReader.read("""(panel "MiniHUD"
                (item engine :target fontSize :type slider :value 99))
                (panel "飞行信息" (group appearance
                    (item flight :target fontSize :type slider :value $offset)))
                (panel "舵面值" (item control :target fontSize :type slider :value 10))""")
            assertEquals(ReadingTextSizes.fromLegacyOffset(offset), imported.flightTextSizes)
            assertEquals(listOf("engine", "control"), imported.unmigrated.map { it.label })
            assertFalse(imported.hasChanges)
            val before = AppSettings(hudSceneLayout = HudSceneLayout(500, 500, listOf(
                HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 500, 200),
                HudRegion("engine", HudRegionContent.ENGINE, 0, 250, 500, 200))))
            assertEquals(before, imported.applyTo(before))
            val after = imported.copy(importFlightTextSizes = true).applyTo(before)
            assertEquals(ReadingTextSizes.fromLegacyOffset(offset), after.hudSceneLayout!!.regions.first().readingTextSizes)
            assertEquals(before.hudSceneLayout!!.regions[1], after.hudSceneLayout!!.regions[1])
        }
    }

    @Test fun explicitAttributeWinsAndAmbiguousRowsAreRejected() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :font-size -4
            (item f :target fontSize :type slider :value 10))""")
        assertEquals(ReadingTextSizes(10f, 20f, 10f), imported.flightTextSizes)
        for (value in listOf("-7", "21", "0.5", "true"))
            assertFails { LegacySettingsReader.read("""(panel "飞行信息" (item f :target fontSize :type slider :value $value))""") }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息"
            (item a :target fontSize :type slider :value 1)
            (group x (item b :target fontSize :type slider :value 2)))""") }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" (item f :target fontSize :type input :value 1))""") }
    }
}
