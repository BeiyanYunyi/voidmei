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
import voidmei.fm.*
import voidmei.telemetry.*

class HudWepStatusGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun upperBoundsExplainMissingModelsInactiveConsumptionAndSamplingReset() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("""
            Mass { MaxNitro:r=10 }
            Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.5 } }
        """))
        val matching = AircraftAlertModel("test", parameters)
        val base = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110}""",
            """{"valid":true,"type":"test"}""")!!
        val monitor = WepFuelMonitor()
        fun sample(throttle: Double?, time: Long): ConnectionState.Flying {
            val telemetry = base.copy(engines = base.engines.map { it.copy(throttlePercent = throttle) })
            return monitor.update(ConnectionState.Flying(telemetry, FlightMetrics()), matching, time) as ConnectionState.Flying
        }
        var flight by mutableStateOf(sample(110.0, 0))
        var model by mutableStateOf<AircraftAlertModel?>(null)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("wep_fuel", "wep_time"),
            hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.size(600.dp, 500.dp)) {
            HudPanel(flight, settings, emptyList(), model) {}
        } } }
        compose.onNodeWithText("当前机型缺少可用 WEP 燃料模型。").assertIsDisplayed()
        compose.onAllNodesWithText("WEP 为估算上限", substring = true).assertCountEquals(1)
        compose.runOnIdle { model = matching.copy(aircraft = "other") }
        compose.onNodeWithText("当前机型缺少可用 WEP 燃料模型。").assertIsDisplayed()
        compose.runOnIdle { model = matching; flight = sample(110.0, 1000) }
        compose.onNodeWithText("9.5 kg").assertIsDisplayed()
        compose.onNodeWithText("00:19").assertIsDisplayed()
        compose.onNodeWithText("当前机型缺少可用 WEP 燃料模型。").assertDoesNotExist()
        compose.runOnIdle { flight = sample(100.0, 2000) }
        compose.onNodeWithText("9.0 kg").assertIsDisplayed()
        compose.onNodeWithText("当前未观测到 WEP 消耗，续航时间未知。").assertIsDisplayed()
        compose.runOnIdle { flight = sample(null, 3000) }
        compose.onNodeWithText("暂无有效 WEP 估算", substring = true).assertIsDisplayed()
        compose.onNodeWithText("9.0 kg").assertDoesNotExist()
        compose.runOnIdle { flight = sample(110.0, 4000) }
        compose.onNodeWithText("10.0 kg").assertIsDisplayed()
        compose.onNodeWithText("00:20").assertIsDisplayed()
        compose.onNodeWithText("不表示实际补满", substring = true).assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("ias")) }
        compose.onNodeWithText("WEP 为估算上限", substring = true).assertDoesNotExist()
    }
}
