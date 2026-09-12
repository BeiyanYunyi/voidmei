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
import voidmei.config.AppSettings
import voidmei.telemetry.*

class HudOilPressureGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rawGaugeShowsUnitsWithoutBorrowingFuelPressureAlertsAndClearsOnDelay() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"throttle 1, %":100,"throttle 2, %":100}""",
            """{"valid":true,"type":"test","oil_pressure":2.75}""")!!
        var state by mutableStateOf<ConnectionState>(ConnectionState.Flying(telemetry, FlightMetrics()))
        var settings by mutableStateOf(AppSettings(hudFields = listOf("oil_pressure_raw"),
            hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 350.dp)) {
            HudPanel(state, settings, listOf(FlightAlert.LOW_FUEL_PRESSURE), null) {}
        } } }
        compose.onNodeWithText("滑油压力原值").assertIsDisplayed()
        compose.onNodeWithText("2.75 仪表单位").assertIsDisplayed().assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithText("单位及发动机归属未确定", substring = true).assertIsDisplayed()
        compose.runOnIdle { state = ConnectionState.Delayed }
        compose.onNodeWithText("2.75 仪表单位").assertDoesNotExist()
        compose.runOnIdle { state = ConnectionState.Flying(telemetry.copy(oilPressureRaw = null), FlightMetrics()) }
        compose.onNodeWithText("— 仪表单位").assertIsDisplayed()
        compose.runOnIdle { state = ConnectionState.Flying(telemetry.copy(oilPressureRaw = 0.0), FlightMetrics()) }
        compose.onNodeWithText("0.00 仪表单位").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithText("滑油压力原值").assertDoesNotExist()
        compose.onNodeWithText("单位及发动机归属未确定", substring = true).assertDoesNotExist()
    }
}
