package voidmei.config

import kotlin.test.*

class LegacyAlphaReportTest {
    @Test fun savedAlphaIsReportedWithoutChangingEitherTransparency() {
        for (title in listOf("飞行信息", "地平仪", "舵面值", "起落襟翼")) {
            val imported = LegacySettingsReader.read("""(panel "$title" :alpha 180)""")
            assertFalse(imported.hasChanges)
            assertEquals(":alpha", imported.unmigrated.single().target)
            assertTrue(imported.unmigrated.single().label.startsWith(title))
            val before = AppSettings(hudOpacity = .3f, hudSceneLayout = HudSceneLayout(500, 300,
                listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 500, 300,
                    backgroundAlpha = .2f, contentAlpha = .8f))))
            assertEquals(before, imported.applyTo(before))
        }
    }

    @Test fun reportDoesNotBlockOtherSettingsOrConfuseAnItemLabelForAnAttribute() {
        val imported = LegacySettingsReader.read("""(panel "飞行信息" :alpha 180
            (item port :target httpPort :type input :value 8112))""")
        assertTrue(imported.hasChanges)
        assertEquals(":alpha", imported.unmigrated.single().target)
        assertTrue(imported.applyTo(AppSettings()).endpoint.contains(":8112"))
        val label = LegacySettingsReader.read("""(panel "飞行信息"
            (item ":alpha" :target httpPort :type input :value 8112))""")
        assertTrue(label.unmigrated.isEmpty())
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" :alpha)""") }
        assertFails { LegacySettingsReader.read("""(panel "飞行信息" :alpha 1 :alpha 2)""") }
    }
}
