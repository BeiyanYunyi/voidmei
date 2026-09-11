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

class HudFuelRateGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enduranceExplainsWarmupZeroLossAndMissingFuelInBothFormats() {
        val base = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":100}""",
            """{"valid":true,"type":"test"}""")!!
        val calculator = FlightCalculator()
        fun sample(fuel: Double?, second: Int): ConnectionState.Flying {
            val telemetry = base.copy(fuelKg = fuel)
            return ConnectionState.Flying(telemetry, calculator.update(telemetry, second * 1000L))
        }
        var flight by mutableStateOf(sample(100.0, 0))
        var settings by mutableStateOf(AppSettings(hudFields = listOf("endurance"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 300.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("— min").assertIsDisplayed()
        compose.onNodeWithText("等待连续燃油采样", substring = true).assertIsDisplayed()
        compose.runOnIdle { for (second in 1..10) flight = sample(100.0, second) }
        compose.onNodeWithText("未测得燃油减少", substring = true).assertIsDisplayed()
        compose.onNodeWithText("— min").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("endurance_clock", "fuel_loss_rate")) }
        compose.onAllNodesWithTag("hud-fuel-estimate-status").assertCountEquals(1)
        compose.onNodeWithText("未测得燃油减少", substring = true).assertIsDisplayed()
        compose.runOnIdle { flight = sample(89.0, 11) }
        compose.onNodeWithText("01:29").assertIsDisplayed()
        compose.onNodeWithText("60.0 kg/min").assertIsDisplayed()
        compose.onNodeWithText("未测得燃油减少", substring = true).assertDoesNotExist()
        compose.runOnIdle { flight = sample(null, 12) }
        compose.onNodeWithText("缺少燃油量数据", substring = true).assertIsDisplayed()
        compose.runOnIdle { flight = sample(200.0, 13) }
        compose.onNodeWithText("等待连续燃油采样", substring = true).assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithTag("hud-fuel-estimate-status").assertDoesNotExist()
    }

    @Test fun fuelRateDistinguishesWarmupLossZeroRefuellingAndMissingData() {
        val base = TelemetryParser.parse("""{"valid":true,"Mfuel, kg":100,"Mfuel0, kg":300}""",
            """{"valid":true,"type":"test"}""")!!
        val calculator = FlightCalculator()
        fun sample(fuel: Double?, second: Int): ConnectionState.Flying {
            val telemetry = base.copy(fuelKg = fuel)
            return ConnectionState.Flying(telemetry, calculator.update(telemetry, second * 1000L))
        }
        var flight by mutableStateOf(sample(100.0, 0))
        var settings by mutableStateOf(AppSettings(hudFields = listOf("fuel_loss_rate"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(440.dp, 260.dp)) {
            HudPanel(flight, settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("燃油减少率估计").assertIsDisplayed()
        compose.onNodeWithText("— kg/min").assertIsDisplayed()
        compose.onNodeWithText("包含泄漏和抛弃油箱", substring = true).assertIsDisplayed()
        compose.runOnIdle { for (second in 1..9) flight = sample(100.0 - second, second) }
        compose.onNodeWithText("— kg/min").assertIsDisplayed()
        compose.runOnIdle { flight = sample(90.0, 10) }
        compose.onNodeWithText("60.0 kg/min").assertIsDisplayed()
        compose.runOnIdle { flight = sample(200.0, 11) }
        compose.onNodeWithText("— kg/min").assertIsDisplayed()
        compose.runOnIdle { for (second in 12..21) flight = sample(200.0, second) }
        compose.onNodeWithText("0.0 kg/min").assertIsDisplayed()
        compose.runOnIdle { flight = sample(null, 22) }
        compose.onNodeWithText("— kg/min").assertIsDisplayed()
        compose.runOnIdle { flight = sample(190.0, 23) }
        compose.onNodeWithText("— kg/min").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithText("燃油减少率估计").assertDoesNotExist()
        compose.onNodeWithText("包含泄漏和抛弃油箱", substring = true).assertDoesNotExist()
    }
}
