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

class HudAltitudeWarningGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun heightWarningsFollowDisplayedSourceAndMissingValuesClearHighlight() {
        var flight by mutableStateOf(hudPreviewFlight().let { it.copy(
            telemetry = it.telemetry.copy(altitudeM = 100.0, radioAltitudeRaw = 20.0, verticalSpeedMps = -20.0),
            metrics = it.metrics.copy(cockpitAltitudeUnit = CockpitAltitudeUnit.METRES)) })
        var mode by mutableStateOf(HudAltitudeMode.SEA_LEVEL)
        var alerts by mutableStateOf(listOf(FlightAlert.ALTITUDE_DESCENT))
        var fields by mutableStateOf(listOf(HudField.ALTITUDE, HudField.CLIMB))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 300.dp)) {
            FlightPanel(flight, compact = true, fields = fields, mechanization = false,
                altitudeMode = mode, readingAlerts = alerts)
        } } }
        fun warning(text: String, alert: FlightAlert) = compose.onNodeWithText(text).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, alert.label))
        warning("100 m", FlightAlert.ALTITUDE_DESCENT)
        warning("-20.0 m/s", FlightAlert.ALTITUDE_DESCENT)
        compose.runOnIdle { alerts = listOf(FlightAlert.TERRAIN_CLOSURE, FlightAlert.HIGH_DESCENT) }
        compose.onNodeWithText("100 m").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        warning("-20.0 m/s", FlightAlert.HIGH_DESCENT)
        compose.runOnIdle { mode = HudAltitudeMode.ALWAYS_RADAR; flight = flight.copy(telemetry = flight.telemetry.copy(altitudeM = null)) }
        warning("20 m · 雷达估计", FlightAlert.TERRAIN_CLOSURE)
        compose.runOnIdle { fields = listOf(HudField.RADIO_ALTITUDE_ESTIMATE) }
        warning("20 m · 按高度表单位推断", FlightAlert.TERRAIN_CLOSURE)
        compose.runOnIdle { fields = listOf(HudField.ALTITUDE); flight = flight.copy(telemetry = flight.telemetry.copy(radioAltitudeRaw = null)) }
        compose.onNodeWithText("— m").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
