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
import voidmei.fm.*
import voidmei.telemetry.*

class HudEngineBudgetGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionsShowTheirOwnBudgetAndOnlyMatchingWarnings() {
        var model by mutableStateOf(AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("""
            Engine0 { Main { Type:t=Inline } Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=10; RecoverTime:r=5 } } }
            Engine1 { Main { Type:t=Inline } Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=400; RecoverTime:r=5 } } }
        """))))
        val telemetry = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110,"water temp 2, C":110}""",
            """{"valid":true,"type":"test"}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics())
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("heat_budget"))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 300,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            val thermal = EngineThermalMonitor().update(flight, model, 0)
            HudPanel(flight, settings, listOf(FlightAlert.ENGINE_OVERHEAT), model, thermal = thermal) {}
        } } }
        compose.onNodeWithText("0.0–10.0 s").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.ENGINE_OVERHEAT.label))
        compose.onNodeWithText("0.0–400.0 s").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithText("0.0–10.0 s").assertDoesNotExist()
        compose.onNodeWithText("0.0–400.0 s").assertDoesNotExist()
        compose.onAllNodesWithText("— s").assertCountEquals(2)
    }
}
