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
import voidmei.fm.LoadLimits
import voidmei.telemetry.*
import kotlin.test.assertTrue

class HudLoadWarningGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadReadingFollowsPositiveAndNegativeLimitWarningsAndClears() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"Ny":10.01}""",
            """{"valid":true}""")!!.copy(loadG = 10.01))
        var limits by mutableStateOf<LoadLimits?>(LoadLimits(-4.0, 10.0))
        var forcedAlert by mutableStateOf(false)
        val settings = AppSettings(hudFields = listOf("load"), hudAttitude = false,
            hudMechanization = false, hudValueColor = "#0000FF", hudWarningColor = "#FF0000")
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightMetrics())
            val alerts = if (forcedAlert) listOf(FlightAlert.LOAD_LIMIT) else
                FlightAlerts().update(flight, null, 0, false, loadLimits = limits).active
            HudPanel(flight, settings, alerts, null) {}
        } } }
        fun check(text: String, warning: Boolean) {
            val node = compose.onNodeWithText(text).assertIsDisplayed()
            if (warning) node.assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, FlightAlert.LOAD_LIMIT.label))
            else node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            val pixels = node.captureToImage().toPixelMap()
            assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                val c = pixels[x, y]
                c.green < .2f && if (warning) c.red > .8f && c.blue < .2f else c.blue > .8f && c.red < .2f
            } }, "Reading color should follow the active load warning")
        }
        check("10.0 G", true) // Rounded text must not suppress a real limit crossing.
        compose.runOnIdle { telemetry = telemetry.copy(loadG = 10.0) }
        check("10.0 G", false)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = -4.01) }
        check("-4.0 G", true)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = -4.0) }
        check("-4.0 G", false)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = 12.0); limits = null }
        check("12.0 G", false)
        compose.runOnIdle { telemetry = telemetry.copy(loadG = null); forcedAlert = true }
        check("— G", false)
    }
}
