package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.*

class HudFuelQuantityGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fuelGaugeAndReadingsFollowQuantityWarningsAndMissingCapacity() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"Mfuel, kg":50,"Mfuel0, kg":100}""",
            """{"valid":true}""")!!)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("fuel", "fuel_percent"), hudAttitude = false,
            hudMechanization = false, hudValueColor = "#0000FF", hudWarningColor = "#FF0000"))
        var waiting by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(450.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0))
            val state = if (waiting) ConnectionState.WaitingForFlight else flight
            val alerts = FlightAlerts().updateForAircraft(state, null, 0, false).active
            HudPanel(state, settings, alerts, null) {}
        } } }
        val gauge = compose.onNodeWithTag("hud-fuel-quantity-bar")
        fun checkReading(text: String, warning: FlightAlert? = null) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            if (warning == null) node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            else node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, warning.label))
            val pixels = node.captureToImage().toPixelMap()
            assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                val c = pixels[x, y]
                c.green < .2f && if (warning == null) c.blue > .8f && c.red < .2f else c.red > .8f && c.blue < .2f
            } })
        }
        fun checkGauge(progress: Float, warning: FlightAlert? = null) {
            gauge.assertIsDisplayed()
            compose.waitUntil { kotlin.math.abs(gauge.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current - progress) < .001f }
            if (warning != null) gauge.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, warning.label))
        }
        checkReading("50 kg")
        checkReading("50.0 %")
        checkGauge(.5f)
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 10.0) }
        checkReading("10 kg", FlightAlert.LOW_FUEL)
        checkReading("10.0 %", FlightAlert.LOW_FUEL)
        checkGauge(.1f, FlightAlert.LOW_FUEL)
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 10.01) }
        checkReading("10.0 %") // Actual ratio, not the rounded caption, controls the alert.
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 0.0) }
        checkReading("0 kg", FlightAlert.EMPTY_FUEL)
        checkReading("0.0 %", FlightAlert.EMPTY_FUEL)
        checkGauge(0f, FlightAlert.EMPTY_FUEL)
        compose.runOnIdle { telemetry = telemetry.copy(fuelCapacityKg = null) }
        checkReading("0 kg", FlightAlert.EMPTY_FUEL)
        checkReading("— %")
        gauge.assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = null) }
        checkReading("— kg")
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 100.0, fuelCapacityKg = 100.0) }
        checkGauge(1f)
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("fuel")) }
        gauge.assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("fuel_percent")); waiting = true }
        gauge.assertDoesNotExist()
    }
}
