package voidmei.config

import kotlin.test.*

class LegacyAttitudeSizeTest {
    @Test fun explicitDpiAndScreenConvertOuterBoundsAndPreserveOtherSettings() {
        val first = HudRegion("attitude", HudRegionContent.ATTITUDE, 800, 500, 100, 100, borderAlpha = .3f)
        val before = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(first, first.copy(id = "two"))))
        val imported = LegacySettingsReader.read("""(panel p
            (item w :target attitudeIndicatorWidth :type slider :value 233)
            (item h :target attitudeIndicatorHeight :type slider :value 174)
            (item b :target enableAttitudeIndicatorEdge :type switch :value true))""")
        assertEquals(before, imported.applyTo(before))
        val selected = imported.copy(importAttitudeSize = true, legacyScreenSize = LegacyScreenSize(1000, 600), legacyDpiScale = 1.5)
        val after = selected.applyTo(before)
        assertEquals(first.copy(x = 626, y = 315, width = 374, height = 285), after.hudSceneLayout!!.regions.first())
        assertEquals(first.copy(id = "two"), after.hudSceneLayout!!.regions[1])
        assertFalse(after.hudSceneLayout!!.regions.first().borderEnabled) // Border migration is independent.
        assertEquals(after, SettingsJson.decode(SettingsJson.encode(after)))
        assertEquals(before, SettingsUndo.capture(before, after)!!.preview(after).settings)
        assertFails { imported.copy(importAttitudeSize = true).applyTo(before) }
    }

    @Test fun creationResizesBeforeFinalPositionClamping() {
        val imported = LegacySettingsReader.read("""(panel "地平仪" :x .8 :y .8
            (item w :target attitudeIndicatorWidth :type slider :value 100)
            (item h :target attitudeIndicatorHeight :type slider :value 100))""")
            .copy(importHudPositions = true, createMissingHudRegions = true, importAttitudeSize = true,
                legacyScreenSize = LegacyScreenSize(1000, 600), legacyDpiScale = 1.0)
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 300, 200)))
        val after = imported.applyToScene(scene)!!
        assertEquals(scene.regions.first(), after.regions.first())
        val added = after.regions.last()
        assertEquals(104, added.width); assertEquals(104, added.height)
        assertEquals(800, added.x); assertEquals(480, added.y)
        assertEquals(after, imported.applyToScene(after))
    }

    @Test fun boundsDefaultsAndInvalidInputsAreHandledExplicitly() {
        val parsed = LegacySettingsReader.read("""(panel p (item w :target attitudeIndicatorWidth :type slider :value 200))""")
        assertEquals(LegacyAttitudeSize(200, 300), parsed.attitudeSize)
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("a", HudRegionContent.ATTITUDE, 0, 0, 300, 200)))
        assertEquals(1000, LegacyAttitudeSize(600, 600, true).applyTo(scene, LegacyScreenSize(100, 100), 2.0).regions.single().width)
        val tiny = LegacyAttitudeSize(100, 100).applyTo(scene, LegacyScreenSize(100000, 100000), 1.0).regions.single()
        assertEquals(80, tiny.width); assertEquals(40, tiny.height)
        for (dpi in listOf(0.0, -1.0, 9.0, Double.NaN)) assertFails { LegacyAttitudeSize().outerSize(dpi) }
        for (value in listOf("99", "601", "100.5", "true"))
            assertFails { LegacySettingsReader.read("(panel p (item w :target attitudeIndicatorWidth :type slider :value $value))") }
    }
}
