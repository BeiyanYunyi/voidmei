package voidmei.config

import kotlin.test.*

class LegacyPanelSwitchTest {
    @Test fun panelSwitchKeysUseVisibilityAndLegacyFalseDefault() {
        for ((key, content) in mapOf("flightInfoSwitch" to HudRegionContent.FLIGHT,
            "enableAxis" to HudRegionContent.CONTROLS, "enableAttitudeIndicator" to HudRegionContent.ATTITUDE,
            "enablegearAndFlaps" to HudRegionContent.MECHANIZATION)) {
            for (value in listOf("true", "false", null)) {
                val imported = LegacySettingsReader.read("(panel custom :switch-key $key ${value?.let { ":visible $it" } ?: ""})")
                assertEquals(mapOf(content to (value == "true")), imported.hudRegionVisibility)
                assertTrue(imported.unmigrated.isEmpty())
                assertFalse(imported.hasChanges)
                val scene = HudSceneLayout(1000, 600, listOf(HudRegion("r", content, 0, 0, 200, 100)))
                assertEquals(value == "true", imported.copy(importHudRegionVisibility = true).applyToScene(scene)!!.regions.single().visible)
            }
        }
    }

    @Test fun samePanelRowWinsButAnEarlierPanelWinsOverLaterRows() {
        val row = """(item x :target enableAxis :type switch-inv :value false)"""
        val samePanel = LegacySettingsReader.read("""(panel p :switch-key enableAxis :visible false (group g $row))""")
        assertEquals(true, samePanel.hudRegionVisibility[HudRegionContent.CONTROLS])
        val earlierPanel = LegacySettingsReader.read("""(panel first :switch-key enableAxis :visible false)(panel later $row)""")
        assertEquals(false, earlierPanel.hudRegionVisibility[HudRegionContent.CONTROLS])
        val earlierRow = LegacySettingsReader.read("""(panel first $row)(panel later :switch-key enableAxis :visible false)""")
        assertEquals(true, earlierRow.hudRegionVisibility[HudRegionContent.CONTROLS])
        val repeated = LegacySettingsReader.read("""(panel first :switch-key enableAxis :visible false)
            (panel later :switch-key enableAxis :visible true)""")
        assertEquals(false, repeated.hudRegionVisibility[HudRegionContent.CONTROLS])
    }

    @Test fun malformedPanelAttributesAreRejectedAndUnknownSwitchIsReported() {
        for (attributes in listOf(":switch-key enableAxis :visible nope", ":switch-key enableAxis :visible",
            ":switch-key enableAxis :visible true :visible false", ":switch-key enableAxis :switch-key enableAxis", ":switch-key :visible true", ":switch-key"))
            assertFails { LegacySettingsReader.read("(panel p $attributes)") }
        val unsupported = LegacySettingsReader.read("""(panel p :switch-key futureSwitch :visible true)""")
        assertTrue(unsupported.hudRegionVisibility.isEmpty())
        assertEquals("futureSwitch", unsupported.unmigrated.single().target)
        assertFalse(unsupported.hasChanges)
    }
}
