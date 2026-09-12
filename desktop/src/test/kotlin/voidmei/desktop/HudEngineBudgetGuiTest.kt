package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.fm.*
import voidmei.telemetry.*

class HudEngineBudgetGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun delayedThermalBudgetReturnsAsAnUncertaintyRangeInsteadOfFullCapacity() {
        val model = AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("""
            Engine0 { Main { Type:t=Inline } Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=10; RecoverTime:r=5 } } }
        """)))
        val telemetry = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110}""",
            """{"valid":true,"type":"test"}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics())
        val monitor = EngineThermalMonitor()
        for (time in 0L..10000L step 2000L) monitor.update(flight, model, time)
        var state by mutableStateOf<ConnectionState>(flight)
        var observation by mutableStateOf(monitor.update(flight, model, 10000))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(400, 300, listOf(
            HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("heat_budget")))))
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 300.dp)) {
            HudPanel(state, settings, emptyList(), model, thermal = observation) {}
        } } }
        compose.onNodeWithText("0.0–0.0 s").assertIsDisplayed()
        compose.runOnIdle {
            state = ConnectionState.Delayed
            observation = monitor.update(state, null, 11000)
        }
        compose.onNodeWithText("0.0–0.0 s").assertDoesNotExist()
        compose.runOnIdle { state = flight; observation = monitor.update(state, model, 12000) }
        compose.onNodeWithText("0.0–4.0 s").assertIsDisplayed()
        compose.onNodeWithText("0.0–10.0 s").assertDoesNotExist()
        compose.runOnIdle { observation = monitor.update(state, model, 14000) }
        compose.onNodeWithText("0.0–2.0 s").assertIsDisplayed()
    }

    @Test fun previewSuppliesDataAndThermalModelsForSelectedHigherEngineIndices() {
        var missing by mutableStateOf(false)
        var index by mutableStateOf(4)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 400.dp)) {
            HudLayoutPreview(AppSettings(hudSceneLayout = HudSceneLayout(500, 400,
                listOf(HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 500, 400, engineIndex = index,
                    fields = listOf("rpm", "heat_budget"))))), warnings = true, missing = missing)
        } } }
        compose.onNodeWithText("发动机 #4").assertIsDisplayed()
        compose.onNodeWithText("3200 RPM").assertIsDisplayed()
        compose.onNodeWithText("0.0–200.0 s").assertIsDisplayed()
        compose.runOnIdle { index = 10 }
        compose.onNodeWithText("发动机 #10").assertIsDisplayed()
        compose.onNodeWithText("0.0–200.0 s").assertIsDisplayed()
        compose.runOnIdle { missing = true }
        compose.onNodeWithText("3200 RPM").assertDoesNotExist()
        compose.onNodeWithText("— RPM").assertIsDisplayed()
        compose.onNodeWithText("— s").assertIsDisplayed()
        compose.onNodeWithText("此编号无可用发动机数据").assertDoesNotExist()
    }

    @Test fun regionsShowTheirOwnBudgetAndOnlyMatchingWarnings() {
        var model by mutableStateOf(AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("""
            Engine0 { Main { Type:t=Inline } Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=10; RecoverTime:r=5 } } }
            Engine1 { Main { Type:t=Inline } Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=400; RecoverTime:r=5 } } }
        """))))
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"water temp 1, C":110,"water temp 2, C":110}""",
            """{"valid":true,"type":"test"}""")!!)
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("heat_budget"))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 300,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            val flight = ConnectionState.Flying(telemetry, FlightMetrics())
            val thermal = EngineThermalMonitor().update(flight, model, 0)
            HudPanel(flight, settings, listOf(FlightAlert.ENGINE_OVERHEAT), model, thermal = thermal) {}
        } } }
        compose.onNodeWithText("0.0–10.0 s").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.ENGINE_OVERHEAT.label))
        compose.onNodeWithText("0.0–400.0 s").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map {
            if (it.index == 2) it.copy(waterTemperatureC = null) else it
        }) }
        compose.onNodeWithText("当前无可用的 2 号发动机计时预算。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("缺少有效水温（°C）。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("当前无可用的 1 号发动机计时预算。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0.0–10.0 s").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map {
            if (it.index == 2) it.copy(waterTemperatureC = 90.0) else it
        }) }
        compose.onNodeWithText("缺少有效水温（°C）。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("当前温度未进入模型计时档位。", substring = true).assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map {
            if (it.index == 2) it.copy(waterTemperatureC = 110.0) else it
        }) }
        compose.onNodeWithText("当前温度未进入模型计时档位。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0.0–400.0 s").assertIsDisplayed()
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithText("0.0–10.0 s").assertDoesNotExist()
        compose.onNodeWithText("0.0–400.0 s").assertDoesNotExist()
        compose.onAllNodesWithText("— s").assertCountEquals(2)
        compose.onNodeWithText("缺少 1 号发动机温度模型。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("缺少 2 号发动机温度模型。", substring = true).assertDoesNotExist()
    }
}
