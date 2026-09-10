package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import voidmei.fm.*

class PowerComparisonGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun drawsBothCurvesAndSwitchesEnginesWithoutFillingMissingWep() {
        fun engine(power: Double, wep: Boolean): PistonModels {
            val stage = CompressorStage(3000.0, power * 1.2, power)
            return PistonModels(PistonMilitaryModel(listOf(stage), 2400.0),
                if (wep) listOf(stage.copy(wepPowerMult = 1.5)) else null, null)
        }
        val base = FlightModelParameters(1000.0, 100.0, emptyList(), false, emptyList())
        val left = NamedModel("left", base.copy(engineCompressors = mapOf(1 to engine(1000.0, true))))
        val right = NamedModel("right", base.copy(engineCompressors = mapOf(2 to engine(1200.0, false), 3 to engine(1400.0, false))))
        var current by mutableStateOf<NamedModel?>(right)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelComparisonPanel(left, current)
        } } }
        compose.onNodeWithText("展开功率叠加比较").performScrollTo().performClick()
        fun waitReadout(text: String) = compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        waitReadout("高度 0 m · 基准 1000.00 hp · 当前 1200.00 hp")
        val pixels = compose.onNodeWithTag("power-comparison-plot").performScrollTo().captureToImage().toPixelMap()
        var green = 0
        var orange = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.green > 0.7f && color.green > color.red + 0.15f && color.blue > 0.6f) green++
            if (color.red > 0.85f && color.green > 0.5f && color.blue < 0.5f) orange++
        }
        assertTrue(green > 10 && orange > 10)
        compose.onNodeWithText("当前发动机 #3").performScrollTo().performClick()
        waitReadout("高度 0 m · 基准 1000.00 hp · 当前 1400.00 hp")
        compose.onNodeWithText("WEP 比较").performScrollTo().performClick()
        waitReadout("高度 0 m · 基准 1500.00 hp · 当前 — hp")
        compose.onNodeWithText("当前：WEP 不可用").assertExists()
        compose.runOnIdle { current = null }
        compose.onNodeWithTag("power-comparison-plot").assertDoesNotExist()
        compose.onNodeWithText("等待当前模型，功率比较条件已保留。").assertExists()
        compose.runOnIdle { current = right.copy(aircraft = "reloaded") }
        waitReadout("高度 0 m · 基准 1500.00 hp · 当前 — hp")
        compose.onNodeWithText("当前发动机 #3").assertIsSelected()

        compose.onNodeWithText("收起功率叠加比较").performScrollTo().performClick()
        compose.onNodeWithTag("power-comparison-plot").assertDoesNotExist()
        compose.onNodeWithText("展开功率叠加比较").performScrollTo().performClick()
        waitReadout("高度 0 m · 基准 1500.00 hp · 当前 — hp")
        compose.onNodeWithText("当前发动机 #3").assertIsSelected()
    }
}
