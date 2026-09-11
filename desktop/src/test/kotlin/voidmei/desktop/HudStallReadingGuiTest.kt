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

class HudStallReadingGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stallWarningHighlightsOnlyValidIasAndPreservesOverspeedPriority() {
        var flight by mutableStateOf(hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(
            iasKmh = 100.0, tasKmh = 120.0, mach = .1)) })
        var alerts by mutableStateOf(listOf(FlightAlert.STALL_SPEED))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 300.dp)) {
            FlightPanel(flight, compact = true, fields = listOf(HudField.IAS, HudField.TAS, HudField.MACH),
                mechanization = false, readingAlerts = alerts)
        } } }
        fun warning(text: String, alert: FlightAlert) = compose.onNodeWithText(text).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, alert.label))
        warning("100 km/h", FlightAlert.STALL_SPEED)
        compose.onNodeWithText("120 km/h").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("0.10").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { alerts = listOf(FlightAlert.STALL_SPEED, FlightAlert.IAS_LIMIT) }
        warning("100 km/h", FlightAlert.IAS_LIMIT)
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithText("100 km/h").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        for (speed in listOf(null, -1.0, Double.NaN)) {
            compose.runOnIdle { alerts = listOf(FlightAlert.STALL_SPEED); flight = flight.copy(telemetry = flight.telemetry.copy(iasKmh = speed)) }
            compose.onNodeWithText("— km/h").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }
}
