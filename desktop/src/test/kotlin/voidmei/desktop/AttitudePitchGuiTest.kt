package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*

class AttitudePitchGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capturedPitchSignPlacesNoseUpInSkyInBothReferenceFrames() {
        var pitch by mutableStateOf(-20.0)
        var roll by mutableStateOf(0.0)
        var earthFixed by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(300.dp, 300.dp)) {
            val telemetry = TelemetryParser.parse("""{"valid":true}""",
                """{"valid":true,"aviahorizon_pitch":$pitch,"aviahorizon_roll":$roll}""")!!
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = emptyList(), hudMechanization = false, hudAttitudeEarthFixed = earthFixed), emptyList(), null) {}
        } } }
        fun colorAtAircraftHeight(): androidx.compose.ui.graphics.Color {
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            // Away from the gold symbol and ladder strokes; match the aircraft's vertical position.
            val y = if (earthFixed) pixels.height / 2 + (pitch * pixels.height / 65).toInt() else pixels.height / 2
            return pixels[pixels.width / 2 + 65, y]
        }
        for (earth in listOf(false, true)) {
            compose.runOnIdle { earthFixed = earth; roll = 0.0; pitch = -20.0 }
            assertTrue(colorAtAircraftHeight().blue > colorAtAircraftHeight().red, "Nose up must point into blue sky; earthFixed=$earth")
            compose.onNodeWithText("俯仰 20.0°", substring = true).assertExists()
            compose.runOnIdle { pitch = 20.0 }
            assertTrue(colorAtAircraftHeight().red > colorAtAircraftHeight().blue, "Nose down must point into brown ground")
        }
        compose.runOnIdle { earthFixed = false; pitch = 0.0; roll = 180.0 }
        val inverted = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
        assertTrue(inverted[2, 10].red > inverted[2, 10].blue)
        assertTrue(inverted[2, inverted.height - 10].blue > inverted[2, inverted.height - 10].red)
    }
}
