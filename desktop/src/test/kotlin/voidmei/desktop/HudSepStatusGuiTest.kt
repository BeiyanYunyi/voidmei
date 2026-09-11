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

class HudSepStatusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingSepExplainsSamplingInputsAndLongIntervalsThenClearsOnRecovery() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,"Vy, m/s":2}""",
            """{"valid":true,"type":"test"}""")!!
        val calculator = FlightCalculator()
        fun sample(t: Telemetry, time: Long) = ConnectionState.Flying(t, calculator.update(t, time))
        var state by mutableStateOf<ConnectionState>(sample(telemetry, 0))
        var settings by mutableStateOf(AppSettings(hudFields = listOf("sep"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 300.dp)) {
            HudPanel(state, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("— m/s").assertIsDisplayed()
        compose.onNodeWithText("SEP · 等待连续速度采样").assertIsDisplayed()
        compose.runOnIdle { state = sample(telemetry, 100) }
        compose.onNodeWithText("2.0 m/s").assertIsDisplayed()
        compose.onNodeWithTag("hud-sep-status").assertDoesNotExist()
        compose.runOnIdle { state = sample(telemetry.copy(tasKmh = null), 200) }
        compose.onNodeWithText("SEP · 缺少真空速").assertIsDisplayed()
        compose.runOnIdle { state = sample(telemetry.copy(tasKmh = null, verticalSpeedMps = null), 300) }
        compose.onNodeWithText("SEP · 缺少真空速、爬升率").assertIsDisplayed()
        compose.runOnIdle { state = sample(telemetry.copy(verticalSpeedMps = null), 400) }
        compose.onNodeWithText("SEP · 缺少爬升率").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(pollIntervalMs = 5000); state = sample(telemetry, 5000) }
        compose.onNodeWithText("SEP · 刷新间隔过长，请设为 2 秒以内").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(pollIntervalMs = 100) }
        compose.onNodeWithText("SEP · 等待连续速度采样").assertIsDisplayed()
        compose.runOnIdle { state = sample(telemetry, 5100) }
        compose.onNodeWithText("2.0 m/s").assertIsDisplayed()
        compose.onNodeWithTag("hud-sep-status").assertDoesNotExist()
        compose.runOnIdle { state = sample(telemetry, 10000); settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithTag("hud-sep-status").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("sep")); state = ConnectionState.Delayed }
        compose.onNodeWithTag("hud-sep-status").assertDoesNotExist()
    }
}
