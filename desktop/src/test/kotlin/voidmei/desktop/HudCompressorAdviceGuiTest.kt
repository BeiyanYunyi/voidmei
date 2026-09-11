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
import voidmei.config.*
import voidmei.fm.*
import voidmei.telemetry.*

class HudCompressorAdviceGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adviceFollowsEngineFieldsModelAndConnectionInSceneAndVerticalHud() {
        val stages = listOf(CompressorStage(1000.0, 1000.0, 800.0), CompressorStage(1000.0, 1500.0, 1200.0))
        val models = PistonModels(PistonMilitaryModel(stages, 3000.0), stages.reversed(), null)
        var model by mutableStateOf(AircraftAlertModel("test", FlightModelExtractor.extract(BlkParser.parse("Vne:r=800"))
            .copy(engineCompressors = mapOf(1 to models, 2 to models))))
        val telemetry = TelemetryParser.parse(
            """{"valid":true,"H, m":1000,"TAS, km/h":0,"throttle 1, %":100,"compressor stage 1":1,"throttle 2, %":100,"compressor stage 2":2}""",
            """{"valid":true,"type":"test"}""")!!
        val flight = ConnectionState.Flying(telemetry, FlightMetrics())
        var connection by mutableStateOf<ConnectionState>(flight)
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("compressor"))
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false, hudMechanization = false,
            hudEngineIndex = 1, hudEngineFields = listOf("compressor"),
            hudSceneLayout = HudSceneLayout(800, 300, listOf(region, region.copy(id = "two", x = 400, engineIndex = 2)))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 400.dp)) {
            HudPanel(connection, settings, emptyList(), model) {}
        } } }
        compose.onNodeWithTag("hud-compressor-advice-1").assertIsDisplayed()
        compose.onNodeWithTag("hud-compressor-advice-2").assertDoesNotExist()
        compose.onNodeWithText("#1 增压器 1 → 2（基础燃油、15°C 模型估算）").assertIsDisplayed()
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
        compose.runOnIdle { model = model.copy(aircraft = "test"); settings = settings.copy(hudSceneLayout = null) }
        compose.onNodeWithTag("hud-compressor-advice-1").assertIsDisplayed()
        compose.runOnIdle { connection = ConnectionState.Flying(telemetry.copy(tasKmh = null), FlightMetrics()) }
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
        compose.runOnIdle { connection = flight; settings = settings.copy(hudEngineFields = listOf("rpm")) }
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
    }
}
