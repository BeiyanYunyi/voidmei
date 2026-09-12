package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class ReadingTextSizesTest {
    @Test fun oldSizesAreConvertedWithoutClampingAndOnlyApplyWhenSelected() {
        val first = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 500, 250, fontScale = 1.5f)
        val before = AppSettings(hudFontScale = 2f, hudSceneLayout = HudSceneLayout(1000, 600,
            listOf(first, first.copy(id = "two", y = 300))))
        for (offset in -6..20) {
            val imported = LegacySettingsReader.read("""(panel "飞行信息" :font-size $offset)""")
            assertFalse(imported.hasChanges)
            assertEquals(before, imported.applyTo(before))
            val after = imported.copy(importFlightTextSizes = true).applyTo(before)
            val sizes = after.hudSceneLayout!!.regions.first().readingTextSizes!!
            assertEquals((24 + offset).toFloat(), sizes.number)
            assertEquals(((25 + offset) / 2).toFloat(), sizes.label)
            assertEquals(sizes.label, sizes.unit)
            assertEquals(first.copy(fontScale = 1f, readingTextSizes = sizes, readingTextWeights = ReadingTextWeights.legacyFlight), after.hudSceneLayout!!.regions.first())
            assertEquals(before.hudSceneLayout!!.regions[1], after.hudSceneLayout!!.regions[1])
            assertEquals(2f, after.hudFontScale)
            assertEquals(after, SettingsJson.decode(SettingsJson.encode(after)))
            assertEquals(before, SettingsUndo.capture(before, after)!!.preview(after).settings)
        }
    }

    @Test fun sizesValidateAndOtherPanelsRemainUnmapped() {
        for (invalid in listOf(5f, 65f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFails { ReadingTextSizes(label = invalid) }
            assertFails { ReadingTextSizes(number = invalid) }
            assertFails { ReadingTextSizes(unit = invalid) }
        }
        for (invalid in listOf("-7", "21", "0.5", "false", ""))
            assertFails { LegacySettingsReader.read("""(panel "飞行信息" :font-size $invalid)""") }
        val imported = LegacySettingsReader.read("""(panel "MiniHUD" :font-size 6
            (item f :target fontSize :type slider :value 6))""")
        assertNull(imported.flightTextSizes)
        assertEquals("fontSize", imported.unmigrated.single().target)
        assertFails { ReadingTextSizes.fromJson(Json.parseToJsonElement("""{"label":"13","number":14,"unit":14}""")) }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" :font-size 1 :font-size 2)""") }
    }
}
