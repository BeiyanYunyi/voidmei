package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.telemetry.*
import kotlin.test.assertEquals

class HudZeroReadingGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nearZeroReadingsDoNotFlickerSignsAndMissingIsStillUnknown() {
        val original = TelemetryParser.parse("""{"valid":true,"Vy, m/s":-0.01}""", """{"valid":true}""")!!
        var telemetry by mutableStateOf(original)
        var sep by mutableStateOf<Double?>(-0.01)
        compose.setContent { MaterialTheme { Column {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics(specificExcessPowerMps = sep)),
                compact = true, fields = listOf(HudField.CLIMB, HudField.SEP), mechanization = false)
        } } }
        compose.onAllNodesWithText("0.0 m/s").assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(-0.01, original.verticalSpeedMps)
            telemetry = original.copy(verticalSpeedMps = -1.2)
            sep = -1.2
        }
        compose.onAllNodesWithText("-1.2 m/s").assertCountEquals(2)
        compose.runOnIdle { sep = null }
        compose.onNodeWithText("— m/s").assertExists()
    }
}
