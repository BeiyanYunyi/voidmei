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

class RadioAltitudeGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hudSeparatesEstimatedMetresFromRawInstrumentReading() {
        val base = TelemetryParser.parse("""{"valid":true,"H, m":1200}""", """{"valid":true,"radio_altitude":1000}""")!!
        var telemetry by mutableStateOf(base)
        var unit by mutableStateOf<CockpitAltitudeUnit?>(null)
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics(cockpitAltitudeUnit = unit)),
                AppSettings(hudFields = listOf("radio_altitude_estimate", "radio_altitude_raw"),
                    hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } } }
        compose.onNodeWithText("雷达高度估计").assertExists()
        compose.onNodeWithText("1000 仪表单位").assertExists()
        compose.onNodeWithText("— m · 待判定").assertExists()
        compose.runOnIdle { unit = CockpitAltitudeUnit.FEET }
        compose.onNodeWithText("305 m · 按高度表单位推断").assertExists()
        compose.onNodeWithText("1000 仪表单位").assertExists()
        compose.runOnIdle { unit = CockpitAltitudeUnit.METRES }
        compose.onNodeWithText("1000 m · 按高度表单位推断").assertExists()
        compose.runOnIdle { telemetry = base.copy(radioAltitudeRaw = null) }
        compose.onNodeWithText("— m · 按高度表单位推断").assertExists()
        compose.onNodeWithText("— 仪表单位").assertExists()
        compose.runOnIdle { telemetry = base; unit = null }
        compose.onNodeWithText("— m · 待判定").assertExists()
        compose.onNodeWithText("305 m · 按高度表单位推断").assertDoesNotExist()
    }
}
