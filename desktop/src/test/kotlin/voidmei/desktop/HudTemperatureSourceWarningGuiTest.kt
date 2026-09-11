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
import voidmei.fm.*
import voidmei.telemetry.*

class HudTemperatureSourceWarningGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyTheIdentifiedEngineTemperatureChannelReceivesThermalWarning() {
        var model by mutableStateOf(AircraftAlertModel("test", FlightModelParameters(null, null, emptyList(), false, emptyList(),
            engineThermals = listOf(EngineThermalParameters(1, listOf(EngineThermalBand(1, 100.0, 90.0, 200.0, 100.0)))))))
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"water temp 1, C":110,"oil temp 1, C":50}""",
            """{"valid":true,"type":"test","water_temperature":110,"oil_temperature":50}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 300.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightMetrics())
            val thermal = EngineThermalMonitor().update(flight, model, 0)
            FlightPanel(flight, compact = true, fields = listOf(HudField.ENGINE_TEMPERATURE, HudField.OIL_TEMPERATURE),
                mechanization = false, model = model, thermal = thermal, readingAlerts = listOf(FlightAlert.ENGINE_OVERHEAT))
        } } }
        compose.onNodeWithText("110.0 水温仪表原值").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("50.0 油温仪表原值").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { telemetry = telemetry.copy(waterTemperatureRaw = null, oilTemperatureRaw = null) }
        compose.onNodeWithText("110.0 °C · 1号").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.ENGINE_OVERHEAT.label))
        compose.onNodeWithText("50.0 °C · 1号").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle {
            telemetry = telemetry.copy(engines = telemetry.engines.map {
                it.copy(waterTemperatureC = 50.0, oilTemperatureC = 110.0)
            })
        }
        compose.onNodeWithText("50.0 °C · 1号").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("110.0 °C · 1号").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.ENGINE_OVERHEAT.label))
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithText("110.0 °C · 1号").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
