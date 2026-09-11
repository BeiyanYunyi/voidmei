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

class MechanizationStatusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun brakeWarningRequiresActiveAlertAndValidDeployment() {
        var telemetry by mutableStateOf(hudPreviewFlight().telemetry.copy(gearPercent = 0.0, airbrakePercent = 90.0))
        var alerts by mutableStateOf(listOf(FlightAlert.AIRBRAKE_EXTENDED))
        var showBrake by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 250.dp)) {
            MechanizationPanel(telemetry, null, showGear = false, showFlaps = false,
                showAirbrake = showBrake, alerts = alerts)
        } } }
        val warned = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, FlightAlert.AIRBRAKE_EXTENDED.label)
        val normal = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)
        compose.onNodeWithText("减速板 90.0%").assert(warned)
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithText("减速板 90.0%").assert(normal)
        compose.runOnIdle { alerts = listOf(FlightAlert.AIRBRAKE_EXTENDED) }
        for (gear in listOf(100.0, null, -1.0, Double.NaN)) {
            compose.runOnIdle { telemetry = telemetry.copy(gearPercent = gear) }
            compose.onNodeWithText("减速板 90.0%").assert(normal)
        }
        for (brake in listOf(89.9, null, 101.0, Double.POSITIVE_INFINITY)) {
            compose.runOnIdle { telemetry = telemetry.copy(gearPercent = 0.0, airbrakePercent = brake) }
            compose.onNodeWithText("减速板", substring = true).assert(normal)
        }
        compose.runOnIdle { telemetry = telemetry.copy(airbrakePercent = 100.0) }
        compose.onNodeWithText("减速板 100.0%").assert(warned)
        compose.runOnIdle { showBrake = false }
        compose.onNodeWithText("减速板", substring = true).assertDoesNotExist()
    }

    @Test fun flapBarOnlyModeDistinguishesUnknownFromRetractedAndRecovers() {
        var telemetry by mutableStateOf(hudPreviewFlight().telemetry.copy(flapsPercent = 50.0))
        var showBar by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 250.dp)) {
            MechanizationPanel(telemetry, null, showGear = false, showFlaps = false,
                showAirbrake = false, showFlapBar = showBar)
        } } }
        compose.onNodeWithTag("flap-position-bar").assertIsDisplayed()
        for (value in listOf(null, -1.0, 101.0, Double.NaN)) {
            compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = value) }
            compose.onNodeWithTag("flap-position-bar").assertDoesNotExist()
            compose.onNodeWithText("襟翼开度条 · 数据不可用").assertIsDisplayed()
        }
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 0.0) }
        compose.onNodeWithTag("flap-position-bar").assertIsDisplayed()
        compose.onNodeWithText("襟翼开度条 · 0.0%").assertIsDisplayed()
        compose.onNodeWithText("襟翼开度条 · 数据不可用").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = null); showBar = false }
        compose.onNodeWithText("襟翼开度条", substring = true).assertDoesNotExist()
    }
}
