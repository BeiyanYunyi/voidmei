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
import voidmei.fm.*
import voidmei.telemetry.*
import kotlin.test.assertTrue

class HudThermalWarningGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thermalColorsMatchTheEngineChannelAndExactObservation() {
        var model by mutableStateOf(AircraftAlertModel("test", FlightModelParameters(null, null, emptyList(), false, emptyList(),
            engineThermals = (1..2).map { EngineThermalParameters(it, listOf(EngineThermalBand(1, 100.0, 90.0, 299.99, 100.0))) })))
        val original = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110,"oil temp 1, C":50,"water temp 2, C":40,"oil temp 2, C":95}""",
            """{"valid":true,"type":"test"}""")!!
        var telemetry by mutableStateOf(original)
        val old = EngineThermalMonitor().update(ConnectionState.Flying(original, FlightMetrics()), model, 0)
        var stale by mutableStateOf(false)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("heat_tolerance"), hudEngineIndex = 2,
            hudEngineFields = listOf("water_temperature", "oil_temperature"), hudAttitude = false, hudMechanization = false,
            hudValueColor = "#0000FF", hudWarningColor = "#FF0000"))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightMetrics())
            val thermal = if (stale) old else EngineThermalMonitor().update(flight, model, 0)
            HudPanel(flight, settings, listOf(FlightAlert.ENGINE_OVERHEAT), model, thermal = thermal) {}
        } } }
        fun check(text: String, warning: Boolean) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            if (warning) node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, FlightAlert.ENGINE_OVERHEAT.label))
            else node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            val pixels = node.captureToImage().toPixelMap()
            assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                val c = pixels[x, y]
                c.green < .2f && if (warning) c.red > .8f && c.blue < .2f else c.blue > .8f && c.red < .2f
            } })
        }
        compose.onNodeWithText("热预算区间包含未知初始损耗，按采样温度估算，不是实际剩余寿命。").assertDoesNotExist()
        check("40.0 °C", false)
        check("95.0 °C", true)
        check("0.0–300.0 s", true) // 299.99 s is below the limit despite display rounding.
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = 1) }
        check("110.0 °C", true)
        check("50.0 °C", false)
        compose.runOnIdle { model = model.copy(parameters = model.parameters.copy(engineThermals = model.parameters.engineThermals.map {
            it.copy(bands = it.bands.map { band -> band.copy(workSeconds = 300.0) })
        })) }
        check("110.0 °C", false)
        check("0.0–300.0 s", false)
        compose.runOnIdle { stale = true }
        check("110.0 °C", false) // A changed model invalidates the old observation.
        check("— s", false)
        compose.onNodeWithText("当前无可用的 1 号发动机计时预算。", substring = true).assertDoesNotExist()
        compose.runOnIdle { telemetry = original.copy(aircraft = "other") }
        compose.onNodeWithText("缺少 1 号发动机温度模型。", substring = true).assertDoesNotExist()
        check("110.0 °C", false)
        compose.runOnIdle { telemetry = original.copy(engines = original.engines.map { it.copy(waterTemperatureC = null) }) }
        check("— °C", false)
        check("— s", false)
        compose.runOnIdle { settings = settings.copy(hudFields = emptyList()) }
        compose.onNodeWithTag("hud-thermal-budget-status").assertDoesNotExist()
    }
}
