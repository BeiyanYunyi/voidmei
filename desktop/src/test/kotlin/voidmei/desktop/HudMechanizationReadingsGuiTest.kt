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
import voidmei.config.*
import voidmei.telemetry.*

class HudMechanizationReadingsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun separateReadingsShowWarningsAndClearThemAfterRetractionOrMissingData() {
        val scene = HudSceneLayout(600, 300, listOf(HudRegion("controls", HudRegionContent.FLIGHT,
            0, 0, 600, 300, fields = listOf("gear", "flaps", "airbrake"))))
        val settings = AppSettings(hudSceneLayout = scene, hudMechanization = false)
        var flight by mutableStateOf(hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(
            gearPercent = 25.0, flapsPercent = 50.0, airbrakePercent = 95.0)) })
        val alerts = listOf(FlightAlert.GEAR_LIMIT, FlightAlert.FLAP_LIMIT, FlightAlert.AIRBRAKE_EXTENDED)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 300.dp)) {
            HudPanel(flight, settings, alerts, null) {}
        } } }
        for ((text, alert) in listOf("25.0 %" to alerts[0], "50.0 %" to alerts[1], "95.0 %" to alerts[2])) {
            compose.onNodeWithText(text).assertIsDisplayed().assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, alert.label))
        }
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(gearPercent = 100.0)) }
        compose.onNodeWithText("95.0 %").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(
            gearPercent = 0.0, flapsPercent = 0.0, airbrakePercent = 0.0)) }
        compose.onAllNodesWithText("0.0 %").assertCountEquals(3)
            .assertAll(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { flight = flight.copy(telemetry = flight.telemetry.copy(
            gearPercent = null, flapsPercent = -1.0, airbrakePercent = 101.0)) }
        compose.onAllNodesWithText("— %").assertCountEquals(3)
            .assertAll(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
