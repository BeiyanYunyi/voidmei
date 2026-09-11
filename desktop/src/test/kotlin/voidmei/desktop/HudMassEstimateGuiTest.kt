package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.fm.FlightModelParameters
import voidmei.telemetry.*

class HudMassEstimateGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explainsScaleAndEstimatesWhileClearingInvalidInputs() {
        val original = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(aircraft = "test", fuelKg = 4500.0)) }
        var flight by mutableStateOf(original)
        val matching = AircraftAlertModel("test", FlightModelParameters(null, null, emptyList(), false, emptyList(), basicMassKg = 1500.0))
        var model by mutableStateOf<AircraftAlertModel?>(matching)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("mass_estimate", "fuel_mass_share"),
            hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 400.dp)) {
            HudPanel(flight, settings, emptyList(), model) {}
        } } }
        compose.onNodeWithText("6000 kg").assertIsDisplayed()
        compose.onNodeWithText("75.0 %").assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertIsDisplayed()
        compose.onAllNodesWithTag("hud-mass-estimate-status").assertCountEquals(1)
        compose.onNodeWithText("满刻度 50%", substring = true).assertIsDisplayed()
        compose.runOnIdle { model = matching.copy(aircraft = "other") }
        compose.onNodeWithText("缺少当前机型", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertDoesNotExist()
        compose.onNodeWithText("75.0 %").assertDoesNotExist()
        compose.runOnIdle { model = matching; flight = original.copy(telemetry = original.telemetry.copy(fuelKg = null)) }
        compose.onNodeWithText("缺少有效燃油量", substring = true).assertIsDisplayed()
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(fuelKg = 0.0)) }
        compose.onNodeWithText("1500 kg").assertIsDisplayed()
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("mass_estimate")) }
        compose.onNodeWithText("满刻度 50%", substring = true).assertDoesNotExist()
        compose.onNodeWithText("不计弹药、外挂和损伤", substring = true).assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithTag("hud-mass-estimate-status").assertDoesNotExist()
    }
}
