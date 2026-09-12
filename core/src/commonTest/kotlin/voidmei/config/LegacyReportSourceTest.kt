package voidmei.config

import kotlin.test.*

class LegacyReportSourceTest {
    @Test fun duplicateNamesKeepTheirOwnPanelAndNestedGroupPaths() {
        val settings = LegacySettingsReader.read("""(panel "MiniHUD" (group "显示" (group "字体"
            (item "大小" :target fontSize :type slider :value 3))))
            (panel "舵面值" (group "显示" (item "大小" :target fontSize :type slider :value 4)))""")
        assertEquals(listOf(listOf("MiniHUD", "显示", "字体"), listOf("舵面值", "显示")), settings.unmigrated.map { it.sourcePath })
        assertEquals(listOf("fontSize", "fontSize"), settings.unmigrated.map { it.target })
        assertFalse(settings.hasChanges)
        assertEquals(AppSettings(), settings.applyTo(AppSettings()))
    }

    @Test fun deferredValidationReportsAndPanelAttributesAlsoRetainSource() {
        val settings = LegacySettingsReader.read("""(panel "飞行信息" :alpha 180
            (group "外观" (item "字体" :target GlobalTextFont :type combo :value "")))""")
        assertEquals(listOf("飞行信息"), settings.unmigrated.single { it.target == ":alpha" }.sourcePath)
        assertEquals(listOf("飞行信息", "外观"), settings.unmigrated.single { it.target == "GlobalTextFont" }.sourcePath)
        val switch = LegacySettingsReader.read("""(panel "未知窗口" :switch-key unknownSwitch :visible true)""")
        assertEquals(listOf("未知窗口"), switch.unmigrated.single().sourcePath)
    }
}
