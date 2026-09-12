package voidmei.config

import kotlin.test.*

class LegacyFlightColumnsTest {
    @Test fun allOldFlightColumnsAreOptionalAndOnlyChangeFirstFlightRegion() {
        val first = HudRegion("first", HudRegionContent.FLIGHT, 0, 0, 200, 100, readingColumns = 2)
        val current = AppSettings(hudReadingColumns = 1, hudSceneLayout = HudSceneLayout(1000, 600,
            listOf(first, first.copy(id = "second", x = 200))))
        for (columns in 1..16) {
            val imported = LegacySettingsReader.read("""(panel p (item x :target flightInfoColumn :type slider :value $columns))""")
            assertFalse(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(current, imported.applyTo(current))
            val selected = imported.copy(importFlightReadingColumns = true)
            val expected = current.copy(hudSceneLayout = current.hudSceneLayout!!.copy(regions =
                listOf(first.copy(readingColumns = columns), current.hudSceneLayout!!.regions[1])))
            assertEquals(expected, selected.applyTo(current))
            assertEquals(expected, SettingsJson.decode(SettingsJson.encode(expected)))
            assertEquals(AppSettings(), selected.applyTo(AppSettings()))
        }
    }

    @Test fun newlyCreatedFlightRegionUsesColumnsAndInvalidValuesAreRejected() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :x .2 :y .3
            (item x :target flightInfoColumn :type slider :value 12))""").copy(
                importHudPositions = true, createMissingHudRegions = true, importFlightReadingColumns = true)
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 200, 100)))
        val updated = imported.applyToScene(scene)!!
        assertEquals(12, updated.regions.last().readingColumns)
        assertEquals(updated, imported.applyToScene(updated))
        for (value in listOf("0", "17", "1.5", "false"))
            assertFails { LegacySettingsReader.read("(panel p (item x :target flightInfoColumn :type slider :value $value))") }
    }
}
