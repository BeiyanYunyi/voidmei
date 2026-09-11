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
import voidmei.fm.*
import voidmei.telemetry.*

class HudSpeedLimitStatusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explainsMissingInputsAndPreservesOverLimitValues() {
        val wing = WingConfiguration(0.0, 500.0, 1.0, null, null, null, null)
        val matching = AircraftAlertModel("test", FlightModelParameters(null, null, listOf(wing), false, emptyList()))
        val original = hudPreviewFlight().let { it.copy(telemetry = it.telemetry.copy(aircraft = "test", iasKmh = 400.0, mach = 0.5)) }
        var flight by mutableStateOf(original)
        var model by mutableStateOf<AircraftAlertModel?>(matching)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("speed_limit_ratio"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 450.dp)) {
            HudPanel(flight, settings, emptyList(), model) {}
        } } }
        compose.onNodeWithText("80.0 %").assertIsDisplayed()
        compose.onNodeWithText("IAS 限制主导").assertIsDisplayed()
        compose.onNodeWithText("满刻度 100%", substring = true).assertIsDisplayed()
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(mach = 1.25)) }
        compose.onNodeWithText("125.0 %").assertIsDisplayed()
        compose.onNodeWithText("Mach 限制主导").assertIsDisplayed()
        compose.runOnIdle { flight = original.copy(telemetry = original.telemetry.copy(mach = null)) }
        compose.onNodeWithText("缺少有效 IAS 或 Mach 数据。").assertIsDisplayed()
        compose.onNodeWithTag("speed-limit-bar").assertDoesNotExist()
        compose.onNodeWithText("125.0 %").assertDoesNotExist()
        compose.runOnIdle { flight = original; model = matching.copy(aircraft = "other") }
        compose.onNodeWithText("缺少有效速度限制模型", substring = true).assertIsDisplayed()
        compose.runOnIdle { model = matching.copy(parameters = matching.parameters.copy(wings = listOf(wing.copy(maxMach = null)))) }
        compose.onNodeWithText("缺少有效速度限制模型", substring = true).assertIsDisplayed()
        compose.runOnIdle { model = matching; flight = original.copy(telemetry = original.telemetry.copy(iasKmh = 0.0, mach = 0.0)) }
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
        compose.onNodeWithTag("speed-limit-bar").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithTag("speed-limit-status").assertDoesNotExist()
    }
}
