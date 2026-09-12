package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class LegacyModelCategoryGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewIsOptionalAndApplyPreservesOtherModelSelections() {
        val parsed = LegacySettingsReader.read("""(panel p
            (item w :target showWeight :type switch :value false)
            (item s :target showCritSpeed :type switch :value true)
            (item i :target showInertia :type switch :value false)
            (item n :target showNitro :type switch :value false)
            (item h :target showHeatRecovery :type switch :value false)
            (item p0 :target showNoFlapsWing :type switch :value false)
            (item p1 :target showFullFlapsWing :type switch :value false)
            (item p2 :target showFuselage :type switch :value false)
            (item p3 :target showFin :type switch :value false)
            (item p4 :target showStab :type switch :value false)
            (item l :target showMaxLiftLoad :type switch :value false)
            (item lift :target showLift :type switch :value false))""")
        val original = AppSettings(hiddenModelSections = setOf(ModelDetailSection.FLIGHT_LIMITS, ModelDetailSection.STALL, ModelDetailSection.RAW))
        var current by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 650.dp).verticalScroll(rememberScrollState())) {
            LegacySettingsPanel(current.hudSceneLayout, readSettings = { _, _ -> parsed }) { current = it.applyTo(current) }
        } } }
        fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
        click("导入旧版设置"); click("预览旧设置")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("legacy-model-sections").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithText("重量 → 重量：隐藏（未选择）").assertExists()
        compose.onNodeWithTag("legacy-model-sections").performScrollTo().performClick()
        compose.onNodeWithText("临界速度 → 速度与迎角限制：显示（将应用）").assertExists()
        compose.onNodeWithText(LegacyModelCategory.SPEED.note).assertExists()
        compose.onNodeWithText("千米过载 → 升力过载估算：隐藏（将应用）").assertExists()
        compose.onNodeWithText("无襟翼 → 无襟翼器件：隐藏（将应用）").assertExists()
        compose.onNodeWithText("耐热恢复 → 耐热恢复：隐藏（将应用）").assertExists()
        compose.onNodeWithText("加力信息 → 加力燃料：隐藏（将应用）").assertExists()
        compose.onNodeWithText("转动惯量 → 转动惯量：隐藏（将应用）").assertExists()
        compose.onNodeWithText("应用预览设置").assertIsEnabled()
        compose.onNodeWithTag("legacy-model-sections").performScrollTo().performClick()
        compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
        compose.onNodeWithTag("legacy-model-sections").performScrollTo().performClick()
        click("应用预览设置")
        compose.runOnIdle { assertEquals(original.copy(hiddenModelSections = setOf(ModelDetailSection.WEIGHT, ModelDetailSection.STALL, ModelDetailSection.RAW, ModelDetailSection.INERTIA, ModelDetailSection.WEP_FUEL, ModelDetailSection.THERMAL_RECOVERY, ModelDetailSection.CLEAN_PART, ModelDetailSection.FULL_PART, ModelDetailSection.FUSELAGE_PART, ModelDetailSection.FIN_PART, ModelDetailSection.STAB_PART, ModelDetailSection.MAXIMUM_LIFT, ModelDetailSection.LIFT_PARAMETERS)), current) }
    }
}
