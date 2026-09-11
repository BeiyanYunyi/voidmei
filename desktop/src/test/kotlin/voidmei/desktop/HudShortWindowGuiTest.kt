package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.assertTrue

class HudShortWindowGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextAndManyAlertsLeaveScrollableReadingsInAShortWindow() {
        var height by mutableStateOf(180.dp)
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":321,"H, m":1234}""",
            """{"valid":true}""")!!
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(440.dp, height)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = listOf("ias", "altitude"), hudFontScale = 2f, hudAttitude = false, hudMechanization = false),
                FlightAlert.entries, null) { Text("HUD") }
        } } }
        compose.onNodeWithText("321 km/h").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1234 m").performScrollTo().assertIsDisplayed()
        fun alertHeight() = compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val small = alertHeight()
        compose.onNodeWithTag("flight-alert-scrollbar").assertIsDisplayed()
        compose.runOnIdle { height = 600.dp }
        val large = alertHeight()
        assertTrue(large > small)
        assertTrue(large <= HUD_ALERT_HEIGHT_DP.dp)
        compose.onNodeWithText("321 km/h").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { height = 180.dp }
        compose.onNodeWithText("1234 m").performScrollTo().assertIsDisplayed()
    }
}
