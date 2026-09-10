package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*
import java.util.Locale
import kotlin.test.assertNotEquals

class PowerResolutionGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothChartsProbeCriticalAltitudeBetweenOldHundredMetreSamples() {
        val model = PistonModels(PistonMilitaryModel(listOf(CompressorStage(1025.0, 1200.0, 1000.0)), 2400.0), null, null)
        val named = NamedModel("test", FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList(),
            engineCompressors = mapOf(1 to model)))
        var comparison by mutableStateOf(false)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (comparison) PowerComparisonPanel(named, named) else PowerCurvePanel(model)
        } } }
        compose.onNodeWithText("展开功率曲线").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("power-curve-altitude").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("power-curve-altitude").performSemanticsAction(SemanticsActions.SetProgress) { it(1025f) }
        compose.onNodeWithText("高度 1025 m · 军用 1200 hp · WEP — hp").assertExists()
        compose.onNodeWithTag("power-curve-plot").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithText("高度 5000 m", substring = true).assertExists()
        compose.onNodeWithTag("power-curve-altitude").performSemanticsAction(SemanticsActions.SetProgress) { it(1025f) }
        compose.onNodeWithText("军用 按 25 m 采样；峰值仅比较已知点，档位变化区间不跨越缺失数据。").assertExists()
        compose.onNodeWithText("自定义速度 TAS (km/h)").performScrollTo().performTextReplacement("425.5")
        compose.onNodeWithText("自定义海平面温度 (°C)").performScrollTo().performTextReplacement("22.5")
        compose.onNodeWithText("应用功率条件").performScrollTo().performClick()
        compose.onNodeWithText("等效空速 EAS").performScrollTo().performClick()
        compose.onNodeWithText("收起功率曲线").performScrollTo().performClick()
        compose.onNodeWithTag("power-curve-plot").assertDoesNotExist()
        compose.onNodeWithText("展开功率曲线").performScrollTo().performClick()
        compose.onNodeWithText("已应用：EAS 425.50 km/h · 海平面 22.50°C").assertExists()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("power-curve-altitude").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("高度 1025 m", substring = true).assertExists()
        compose.runOnIdle { comparison = true }
        compose.onNodeWithText("展开功率叠加比较").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("功率比较高度").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("功率比较高度").performSemanticsAction(SemanticsActions.SetProgress) { it(1025f) }
        compose.onNodeWithText("高度 1025 m · 基准 1200.00 hp · 当前 1200.00 hp").assertExists()
        compose.onNodeWithTag("power-comparison-plot").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithText("高度 5000 m", substring = true).assertExists()
        compose.onNodeWithText("TAS 600 km/h").performScrollTo().performClick()
        compose.onNodeWithContentDescription("功率比较高度").performSemanticsAction(SemanticsActions.SetProgress) { it(6000f) }
        fun expected(equivalent: Boolean): String {
            val power = PistonPowerModel.optimalPower(model.military.stages, 6000.0, speedKmh = 600.0,
                equivalentAirspeed = equivalent)!!.powerHp
            return String.format(Locale.ROOT, "高度 6000 m · 基准 %.2f hp · 当前 %.2f hp", power, power)
        }
        val tas = expected(false)
        val eas = expected(true)
        assertNotEquals(tas, eas)
        compose.waitUntil(5000) { compose.onAllNodesWithText(tas).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("等效空速 EAS").performScrollTo().performClick()
        compose.onNodeWithText("EAS 600 km/h").assertIsSelected()
        compose.waitUntil(5000) { compose.onAllNodesWithText(eas).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("真空速 TAS").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(tas).fetchSemanticsNodes().isNotEmpty() }

    }
}
