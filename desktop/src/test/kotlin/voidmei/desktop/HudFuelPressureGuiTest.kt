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

class HudFuelPressureGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rawPressureShowsSourceAndFollowsSustainedWarningWithoutAssigningAnEngine() {
        val base = TelemetryParser.parse("""{"valid":true,"throttle 1, %":100}""",
            """{"valid":true,"type":"test","fuel_pressure":9.7}""")!!
        val evaluator = FlightAlerts()
        var flight by mutableStateOf(ConnectionState.Flying(base, FlightMetrics()))
        var alerts by mutableStateOf(evaluator.updateForAircraft(flight, null, 0, false).active)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("fuel_pressure_raw"),
            hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 350.dp)) {
            HudPanel(flight, settings, alerts, null) {}
        } } }
        fun check(text: String, warning: Boolean) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            node.assert(if (warning) SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                FlightAlert.LOW_FUEL_PRESSURE.label) else SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
        check("9.70 仪表单位", false)
        compose.onNodeWithText("单位及发动机归属未确定", substring = true).assertIsDisplayed()
        compose.runOnIdle { alerts = evaluator.updateForAircraft(flight, null, 2000, false).active }
        check("9.70 仪表单位", true)
        compose.runOnIdle {
            flight = flight.copy(telemetry = base.copy(fuelPressureRaw = 10.0))
            alerts = evaluator.updateForAircraft(flight, null, 2100, false).active
        }
        check("10.00 仪表单位", false)
        compose.runOnIdle {
            flight = flight.copy(telemetry = base.copy(engines = base.engines + base.engines.map { it.copy(index = 2) }))
            evaluator.updateForAircraft(flight, null, 2200, false)
            alerts = evaluator.updateForAircraft(flight, null, 4200, false).active
        }
        check("9.70 仪表单位", false)
        compose.runOnIdle { flight = flight.copy(telemetry = base.copy(fuelPressureRaw = null)); alerts = listOf(FlightAlert.LOW_FUEL_PRESSURE) }
        check("— 仪表单位", false)
        compose.runOnIdle { flight = flight.copy(telemetry = base.copy(fuelPressureRaw = 0.0)); alerts = emptyList() }
        check("0.00 仪表单位", false)
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithText("燃油压力原值").assertDoesNotExist()
        compose.onNodeWithText("单位及发动机归属未确定", substring = true).assertDoesNotExist()
    }
}
