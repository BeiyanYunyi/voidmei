package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*

class HudEngineControlBarsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsUseTheirOwnScalesAndRespectValidityAndGraphicsSelection() {
        var engine by mutableStateOf(hudPreviewFlight().telemetry.engines.first().copy(
            index = 2, rpmControlPercent = 50.0, mixturePercent = 60.0))
        var show by mutableStateOf(true)
        var fields by mutableStateOf(listOf(HudEngineField.RPM_CONTROL, HudEngineField.MIXTURE))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 350.dp)) {
            HudEnginePanel(listOf(engine), 2, fields = fields, showInstruments = show)
        } } }
        compose.onNodeWithTag("hud-engine-rpm_control-2").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.onNodeWithTag("hud-engine-mixture-2").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        compose.onNodeWithContentDescription("2 号混合比，满刻度 120%").assertExists()
        compose.runOnIdle { engine = engine.copy(rpmControlPercent = 0.0, mixturePercent = 150.0) }
        compose.onNodeWithTag("hud-engine-rpm_control-2").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.onNodeWithTag("hud-engine-mixture-2").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        compose.onNodeWithText("150 %").assertIsDisplayed()
        compose.runOnIdle { show = false }
        compose.onNodeWithTag("hud-engine-rpm_control-2").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-mixture-2").assertDoesNotExist()
        compose.onNodeWithText("150 %").assertIsDisplayed()
        compose.runOnIdle { show = true; engine = engine.copy(rpmControlPercent = -1.0, mixturePercent = null) }
        compose.onNodeWithTag("hud-engine-rpm_control-2").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-mixture-2").assertDoesNotExist()
        compose.runOnIdle { engine = engine.copy(rpmControlPercent = 100.0, mixturePercent = 120.0); fields = listOf(HudEngineField.MIXTURE) }
        compose.onNodeWithTag("hud-engine-rpm_control-2").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-mixture-2").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
    }
}
