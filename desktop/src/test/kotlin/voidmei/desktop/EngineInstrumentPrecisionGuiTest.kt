package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.EngineControlsLayout
import voidmei.telemetry.*

class EngineInstrumentPrecisionGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun everyLayoutRetainsPowerPrecisionAndActualOverrangeValuesWithoutTable() {
        var layout by mutableStateOf(EngineControlsLayout.HORIZONTAL)
        var power by mutableStateOf<PowerPercentReading?>(PowerPercentReading(75.36, "测试 FM"))
        val engine = hudPreviewFlight().telemetry.engines.first().copy(throttlePercent = 125.0, mixturePercent = 150.0)
        compose.setContent { MaterialTheme { Column(Modifier.size(650.dp, 600.dp)) {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.THROTTLE, HudEngineField.FM_POWER_PERCENT, HudEngineField.MIXTURE),
                powerPercent = power, controlsLayout = layout, showReadings = false)
        } } }
        for (mode in EngineControlsLayout.entries) {
            compose.runOnIdle { layout = mode }
            val horizontalPower = mode == EngineControlsLayout.HORIZONTAL
            compose.onNodeWithText(if (horizontalPower) "1 号动力量 · 测试 FM，满刻度 100% · 75.4%" else "75.4 %").assertExists()
            compose.onNodeWithTag("hud-engine-fm_power_percent-1")
                .assertRangeInfoEquals(ProgressBarRangeInfo(.7536f, 0f..1f))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    if (horizontalPower) "实际 75.4%；满刻度 100%" else "竖向控制条；实际 75.4%；满刻度 100%"))
            compose.onNodeWithTag("hud-engine-throttle-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
            compose.onNodeWithTag("hud-engine-mixture-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
            compose.onNodeWithText(if (mode == EngineControlsLayout.HORIZONTAL) "油门：125 %" else "125 %").assertExists()
            compose.onNodeWithText(if (mode == EngineControlsLayout.VERTICAL) "150 %" else "1 号混合比，满刻度 120% · 150%").assertExists()
        }
        compose.runOnIdle { power = null }
        compose.onNodeWithTag("hud-engine-fm_power_percent-1").assertDoesNotExist()
        compose.onNodeWithText("75.4 %").assertDoesNotExist()
    }
    @Test fun zeroIsVisibleAndNegativeOrNonFiniteControlsAreOmitted() {
        var engine by mutableStateOf(hudPreviewFlight().telemetry.engines.first().copy(radiatorPercent = 0.0, mixturePercent = -1.0))
        compose.setContent { MaterialTheme { Column {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.RADIATOR, HudEngineField.MIXTURE),
                controlsLayout = EngineControlsLayout.VERTICAL, showReadings = false)
        } } }
        compose.onNodeWithTag("hud-engine-radiator-1").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.onNodeWithText("0 %").assertExists()
        compose.onNodeWithTag("hud-engine-mixture-1").assertDoesNotExist()
        compose.runOnIdle { engine = engine.copy(radiatorPercent = Double.NaN, mixturePercent = Double.POSITIVE_INFINITY) }
        compose.onNodeWithTag("hud-engine-radiator-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-mixture-1").assertDoesNotExist()
        compose.onNodeWithText("暂无所选发动机读数").assertExists()
    }
}
