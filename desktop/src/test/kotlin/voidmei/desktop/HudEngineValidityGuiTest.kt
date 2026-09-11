package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*

class HudEngineValidityGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unavailableControlsClearOldValuesAndRecoverAtZeroOrRichMixture() {
        var engine by mutableStateOf(hudPreviewFlight().telemetry.engines.first().copy(rpmControlPercent = 80.0, mixturePercent = 120.0))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 250.dp)) {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.RPM_CONTROL, HudEngineField.MIXTURE))
        } } }
        compose.onNodeWithText("80 %").assertIsDisplayed()
        compose.onNodeWithText("120 %").assertIsDisplayed()
        compose.runOnIdle { engine = engine.copy(rpmControlPercent = -1.0, mixturePercent = -1.0) }
        compose.onAllNodesWithText("— %").assertCountEquals(2)
        compose.onNodeWithText("80 %").assertDoesNotExist()
        compose.onNodeWithText("120 %").assertDoesNotExist()
        compose.runOnIdle { engine = engine.copy(rpmControlPercent = 0.0, mixturePercent = 120.0) }
        compose.onNodeWithText("0 %").assertIsDisplayed()
        compose.onNodeWithText("120 %").assertIsDisplayed()
    }

    @Test fun invalidRpmClearsValueAndHighlightAndZeroThrottleRemainsVisible() {
        var engine by mutableStateOf(hudPreviewFlight().telemetry.engines.first().copy(throttlePercent = 110.0, rpm = 2200.0))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 250.dp)) {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.THROTTLE, HudEngineField.RPM),
                warnings = mapOf(HudEngineField.RPM to FlightAlert.LOW_RPM.label))
        } } }
        compose.onNodeWithText("110 %").assertIsDisplayed()
        compose.onNodeWithText("2200 RPM").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, FlightAlert.LOW_RPM.label))
        compose.runOnIdle { engine = engine.copy(throttlePercent = -1.0, rpm = -1.0) }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithText("— RPM").assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("2200 RPM").assertDoesNotExist()
        compose.runOnIdle { engine = engine.copy(throttlePercent = 0.0, rpm = 0.0) }
        compose.onNodeWithText("0 %").assertIsDisplayed()
        compose.onNodeWithText("0 RPM").assertIsDisplayed()
    }
}
