package voidmei.config

import kotlin.test.*
import kotlinx.serialization.json.*

class ReadingTextWeightsTest {
    @Test fun weightsRoundTripIndependentlyAndOldSettingsInherit() {
        val region = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 500, 250)
        for (label in listOf(null, 400, 700)) for (number in listOf(null, 400, 700)) for (unit in listOf(null, 400, 700)) {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(500, 250,
                listOf(region.copy(readingTextWeights = ReadingTextWeights(label, number, unit)))))
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        val layout = HudSceneLayout(500, 250, listOf(region))
        val old = layout.toJson().jsonObject.toMutableMap()
        old["regions"] = JsonArray(listOf(JsonObject(layout.toJson().jsonObject.getValue("regions").jsonArray.single().jsonObject - "readingTextWeights")))
        assertNull(HudSceneLayout.fromJson(JsonObject(old)).regions.single().readingTextWeights)
        for (weight in listOf(-1, 0, 500, 900)) assertFails { ReadingTextWeights(number = weight) }
        for (raw in listOf("\"700\"", "true", "700.5"))
            assertFails { ReadingTextWeights.fromJson(Json.parseToJsonElement("""{"label":$raw}""")) }
    }

    @Test fun optionalLegacyImportPreservesUserWeightsUntilSelected() {
        val before = AppSettings(hudSceneLayout = HudSceneLayout(500, 250, listOf(HudRegion("flight", HudRegionContent.FLIGHT,
            0, 0, 500, 250, readingTextWeights = ReadingTextWeights(400, 400, 700)))))
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :font-size 6)""")
        assertEquals(before, imported.applyTo(before))
        val after = imported.copy(importFlightTextSizes = true).applyTo(before)
        assertEquals(ReadingTextWeights.legacyFlight, after.hudSceneLayout!!.regions.single().readingTextWeights)
        assertEquals(before, SettingsUndo.capture(before, after)!!.preview(after).settings)
    }
}
