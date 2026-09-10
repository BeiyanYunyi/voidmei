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

class HudEngineGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hudUsesSelectedEngineAndClearsMissingOrAmbiguousData() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"RPM 1":1111,"RPM 10":2222,"power 10, hp":900}""",
            """{"valid":true,"type":"test"}""")!!)
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false,
            hudMechanization = false, hudEngineIndex = 10))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(440.dp, 500.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) { Text("HUD") }
        } } }
        compose.onNodeWithText("发动机 #10").assertIsDisplayed()
        compose.onNodeWithText("2222 RPM").assertIsDisplayed()
        compose.onNodeWithText("900 hp").assertIsDisplayed()
        compose.onNodeWithText("1111 RPM").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.filter { it.index == 1 }) }
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
        compose.onNodeWithText("2222 RPM").assertDoesNotExist()
        compose.onNodeWithText("1111 RPM").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = 1) }
        compose.onNodeWithText("1111 RPM").assertIsDisplayed()
        compose.onNodeWithText("— hp").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines + telemetry.engines.single()) }
        compose.onNodeWithText("此编号无可用发动机数据").assertIsDisplayed()
        compose.onNodeWithText("1111 RPM").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudEngineIndex = null) }
        compose.onNodeWithText("发动机 #1").assertDoesNotExist()
    }
}
