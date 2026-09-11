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
import voidmei.config.AppSettings
import voidmei.telemetry.*

class HudDelayedTelemetryGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun delayedTelemetryClearsFlightEngineGaugeAndAlertsUntilRecovery() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":380,"Mfuel, kg":0,"Mfuel0, kg":100,"RPM 1":2994}""",
            """{"valid":true,"type":"test","aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0))
        var state by mutableStateOf<ConnectionState>(flight)
        val settings = AppSettings(hudFields = listOf("ias", "fuel_percent"), hudMechanization = false,
            hudEngineIndex = 1, hudEngineFields = listOf("rpm"))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 600.dp)) {
            val alerts = FlightAlerts().updateForAircraft(state, null, 0, false).active
            HudPanel(state, settings, alerts, null) {}
        } } }
        compose.onNodeWithText("380 km/h").assertIsDisplayed()
        compose.onNodeWithText("2994 RPM").assertIsDisplayed()
        compose.onNodeWithTag("hud-fuel-quantity-bar").assertIsDisplayed()
        compose.onNodeWithText("燃油耗尽", substring = true).assertExists()
        compose.runOnIdle { state = ConnectionState.Delayed }
        compose.onNodeWithText("遥测更新延迟 · 等待当前请求").assertIsDisplayed()
        compose.onNodeWithText("380 km/h").assertDoesNotExist()
        compose.onNodeWithText("2994 RPM").assertDoesNotExist()
        compose.onNodeWithTag("hud-fuel-quantity-bar").assertDoesNotExist()
        compose.onNodeWithText("燃油耗尽", substring = true).assertDoesNotExist()
        compose.onNodeWithText("俯仰", substring = true).assertDoesNotExist()
        compose.runOnIdle { state = flight }
        compose.onNodeWithText("380 km/h").assertIsDisplayed()
        compose.onNodeWithText("2994 RPM").assertIsDisplayed()
        compose.onNodeWithTag("hud-fuel-quantity-bar").assertIsDisplayed()
        compose.onNodeWithText("遥测更新延迟 · 等待当前请求").assertDoesNotExist()
    }
}
