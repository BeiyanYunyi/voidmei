package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class RegionReadingFontTest {
    private val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 250)
    private val before = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600,
        listOf(region, region.copy(id = "two", y = 300, readingLabelFont = "serif")) ))

    @Test fun fontsRoundTripAndOldScenesInherit() {
        val modified = before.copy(hudSceneLayout = before.hudSceneLayout!!.copy(regions = listOf(
            region.copy(readingLabelFont = "sans-serif", readingNumberFont = "monospace"))))
        assertEquals(modified, SettingsJson.decode(SettingsJson.encode(modified)))
        val old = before.hudSceneLayout!!.toJson().jsonObject.toMutableMap()
        old["regions"] = JsonArray(old.getValue("regions").jsonArray.map { JsonObject(it.jsonObject - "readingLabelFont" - "readingNumberFont") })
        assertTrue(HudSceneLayout.fromJson(JsonObject(old)).regions.all { it.readingLabelFont == null && it.readingNumberFont == null })
        for (invalid in listOf("", " ", "a\nb", "a".repeat(201))) {
            assertFails { region.copy(readingLabelFont = invalid) }
            assertFails { region.copy(readingNumberFont = invalid) }
        }
    }

    @Test fun flightLabelImportRequiresSelectionAndCanBeUndone() {
        val imported = LegacySettingsReader.read("""(panel p
            (item font :target flightInfoFontC :type combo :value "sans-serif")
            (item size :target fontSize :type slider :value 4))""")
        assertFalse(imported.hasChanges)
        assertEquals(listOf("fontSize"), imported.unmigrated.map { it.target })
        assertEquals(before, imported.applyTo(before))
        val selected = imported.copy(importFlightLabelFont = true)
        val after = selected.applyTo(before)
        assertEquals(region.copy(readingLabelFont = "sans-serif"), after.hudSceneLayout!!.regions.first())
        assertEquals(before.hudSceneLayout!!.regions[1], after.hudSceneLayout!!.regions[1])
        assertEquals(before, SettingsUndo.capture(before, after)!!.preview(after).settings)
        assertEquals(AppSettings(), selected.applyTo(AppSettings()))
        val created = LegacySettingsReader.read("""(panel "飞行信息" :x .1 :y .1
            (item font :target flightInfoFontC :type combo :value "serif"))""")
            .copy(importFlightLabelFont = true, importHudPositions = true, createMissingHudRegions = true)
            .applyToScene(HudSceneLayout(1000, 600, listOf(region.copy(content = HudRegionContent.ENGINE))))!!
        assertEquals("serif", created.regions.last().readingLabelFont)
    }

    @Test fun panelFontTakesPrecedenceOverHistoricalRow() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :font "sans-serif"
            (item f :target flightInfoFontC :type combo :value serif))""")
        assertEquals("sans-serif", imported.flightLabelFont)
        assertEquals("monospace", LegacySettingsReader.read("""(panel "飞行信息" :font monospace)""").flightLabelFont)
        for (attrs in listOf(":font", ":font :x .2", ":font serif :font monospace"))
            assertFails { LegacySettingsReader.read("""(panel "飞行信息" $attrs)""") }
    }

    @Test fun invalidFontIsReportedAndWrongTypeRejected() {
        val invalid = LegacySettingsReader.read("""(panel p (item f :target flightInfoFontC :type combo :value ""))""")
        assertNull(invalid.flightLabelFont)
        assertEquals("flightInfoFontC", invalid.unmigrated.single().target)
        assertFails { LegacySettingsReader.read("""(panel p (item f :target flightInfoFontC :type slider :value 12))""") }
    }
}
