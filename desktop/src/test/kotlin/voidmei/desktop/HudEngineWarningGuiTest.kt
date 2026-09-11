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

class HudEngineWarningGuiTest {
    @get:Rule val compose = createComposeRule()
    private val model = AircraftAlertModel("test", FlightModelParameters(null, null, emptyList(), false, emptyList(),
        engineRpmLimits = listOf(EngineRpmLimit(1, 3000.0), EngineRpmLimit(2, 3000.0)),
        engineRpmReferences = listOf(EngineRpmReference(1, 2000.0, "jet"), EngineRpmReference(2, 2000.0, "jet"))))
    private fun engine(index: Int, rpm: Double) = Engine(index, 100.0, rpm, null, 500.0, null, null)
    private val initial = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!

    private fun check(text: String, warning: FlightAlert? = null) {
        val node = compose.onNodeWithText(text).assertIsDisplayed()
        if (warning == null) node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        else node.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, warning.label))
        val pixels = node.captureToImage().toPixelMap()
        assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
            val c = pixels[x, y]
            c.green < .2f && if (warning == null) c.blue > .8f && c.red < .2f else c.red > .8f && c.blue < .2f
        } })
    }

    private fun settings() = AppSettings(hudFields = emptyList(), hudEngineIndex = 2,
        hudEngineFields = listOf("rpm", "thrust"), hudAttitude = false, hudMechanization = false,
        hudValueColor = "#0000FF", hudWarningColor = "#FF0000")

    @Test fun selectedEngineWarningsDoNotLeakAcrossEnginesOrAircraft() {
        var telemetry by mutableStateOf(initial.copy(engines = listOf(engine(1, 4000.0), engine(2, 1500.0))))
        var settings by mutableStateOf(settings())
        // Keep global alerts present to exercise per-sample and per-engine filtering of stale alerts too.
        val alerts = listOf(FlightAlert.HIGH_RPM, FlightAlert.LOW_RPM, FlightAlert.NEGATIVE_LOAD_LOW_THRUST)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, alerts, model) {}
        } } }
        check("1500 RPM")
        check("500 kgf")
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = 1) }
        check("4000 RPM", FlightAlert.HIGH_RPM)
        compose.runOnIdle { telemetry = telemetry.copy(engines = listOf(engine(1, 1000.0), engine(2, 1500.0))) }
        check("1000 RPM", FlightAlert.LOW_RPM)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = -1.0,
            engines = listOf(engine(1, 1000.0).copy(thrustKgf = 10.0), engine(2, 1500.0))) }
        check("10 kgf", FlightAlert.NEGATIVE_LOAD_LOW_THRUST)
        check("1000 RPM") // This symptom suppresses the separate low-RPM diagnosis.
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "other", loadG = 1.0,
            engines = listOf(engine(1, 4000.0))) }
        check("4000 RPM")
        compose.runOnIdle { telemetry = telemetry.copy(engines = listOf(engine(1, 4000.0).copy(rpm = null))) }
        check("— RPM")
        compose.runOnIdle { telemetry = telemetry.copy(engines = listOf(engine(1, 4000.0), engine(1, 1000.0))) }
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
        compose.onNodeWithText("4000 RPM").assertDoesNotExist()
    }

    @Test fun standaloneEngineOneFieldsFollowActualActiveWarnings() {
        var telemetry by mutableStateOf(initial.copy(engines = listOf(engine(1, 4000.0))))
        val settings = settings().copy(hudEngineIndex = null, hudFields = listOf("engine1_thrust", "engine1_rpm"))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightMetrics())
            val alerts = FlightAlerts().updateForAircraft(flight, model, 0, false).active
            HudPanel(flight, settings, alerts, model) {}
        } } }
        check("4000 RPM", FlightAlert.HIGH_RPM)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = -1.0,
            engines = listOf(engine(1, 1500.0).copy(thrustKgf = 10.0))) }
        check("1500 RPM")
        check("10 kgf", FlightAlert.NEGATIVE_LOAD_LOW_THRUST)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = 1.0) }
        check("10 kgf")
    }
}
