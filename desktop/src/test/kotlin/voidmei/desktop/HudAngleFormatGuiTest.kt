package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.fm.*
import voidmei.telemetry.*
import kotlin.test.assertEquals

class HudAngleFormatGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun attitudeCaptionsNormalizeZeroInBothCompleteAndPartialData() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0.01,"aviahorizon_roll":-0.01}""")!!)
        compose.setContent { MaterialTheme { AttitudePanel(telemetry, compact = true) } }
        compose.onNodeWithText("俯仰 0.0° · 横滚 0.0°", substring = true).assertExists()
        compose.runOnIdle {
            assertEquals(0.01, telemetry.pitchDeg)
            telemetry = telemetry.copy(rollDeg = null)
        }
        compose.onNodeWithText("俯仰 0.0° · 横滚 —°").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = 1.2, rollDeg = -1.2) }
        compose.onNodeWithText("俯仰 -1.2° · 横滚 -1.2°", substring = true).assertExists()
    }

    @Test fun roundedZeroMarginDoesNotSuppressAnActualLimitWarning() {
        val wing = WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)
        val model = AircraftAlertModel("test", FlightModelParameters(null, null, listOf(wing), false, emptyList()))
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"AoA, deg":20.01,"flaps, %":0}""",
            """{"valid":true,"type":"test"}""")!!)
        compose.setContent { MaterialTheme { Column { AoaMarginPanel(telemetry, model, warningPercent = 0.0) } } }
        compose.onNodeWithText("距模型正迎角限 0.0°").assertExists()
        compose.onNodeWithText("已达模型正迎角限").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(angleOfAttackDeg = 19.99) }
        compose.onNodeWithText("距模型正迎角限 0.0°").assertExists()
        compose.onNodeWithText("已达模型正迎角限").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(angleOfAttackDeg = null) }
        compose.onNodeWithText("距模型正迎角限 —").assertExists()
        compose.onNodeWithTag("aoa-margin-bar").assertDoesNotExist()
    }
}
