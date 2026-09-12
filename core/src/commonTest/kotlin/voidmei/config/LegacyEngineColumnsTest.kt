package voidmei.config

import kotlin.test.*

class LegacyEngineColumnsTest {
    @Test fun scopedColumnsRequireExplicitEngineDestinationAndPreserveOtherSettings() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 10, 20, 300, 200, fields = listOf("rpm"))
        val scene = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two", readingColumns = 2),
            one.copy(id = "flight", content = HudRegionContent.FLIGHT)))
        val current = AppSettings(hudReadingColumns = 4, hudSceneLayout = scene)
        for (columns in 1..8) {
            val parsed = LegacySettingsReader.read("""(panel "动力信息" (group "外观"
                (item x :target hudColumns :type slider :value $columns)))
                (panel "其他" (item x :target hudColumns :type slider :value 99))""")
            assertEquals(columns, parsed.engineReadingColumns)
            assertFalse(parsed.hasChanges)
            assertEquals(listOf("其他"), parsed.unmigrated.single().sourcePath)
            assertEquals(current, parsed.applyTo(current))
            val result = parsed.copy(engineColumnsRegionId = "two").applyTo(current)
            assertEquals(current.copy(hudSceneLayout = scene.copy(regions = scene.regions.map {
                if (it.id == "two") it.copy(readingColumns = columns) else it })), result)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
            for (id in listOf("missing", "flight")) assertEquals(current, parsed.copy(engineColumnsRegionId = id).applyTo(current))
        }
    }
    @Test fun malformedAndDuplicateColumnsAreRejected() {
        for (value in listOf("0", "9", "1.5", "false")) assertFails {
            LegacySettingsReader.read("""(panel "动力信息" (item x :target hudColumns :type slider :value $value))""")
        }
        assertFails { LegacySettingsReader.read("""(panel "动力信息"
            (item x :target hudColumns :type slider :value 2)
            (item x :target hudColumns :type slider :value 3))""") }
        assertFails { LegacySettingsReader.read("""(panel "动力信息" (item x :target hudColumns :type switch :value true))""") }
    }
}
