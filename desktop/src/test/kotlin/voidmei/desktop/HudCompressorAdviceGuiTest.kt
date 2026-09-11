package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import voidmei.config.*
import voidmei.fm.*
import voidmei.telemetry.*

class HudCompressorAdviceGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recommendationChangesWithWepAndClearsAfterShiftingOrLosingItsModel() {
        val stages = listOf(CompressorStage(1000.0, 1000.0, 800.0), CompressorStage(1000.0, 1500.0, 1200.0))
        val models = PistonModels(PistonMilitaryModel(stages, 3000.0), stages.reversed(), null)
        val parameters = FlightModelExtractor.extract(BlkParser.parse("Vne:r=800"))
            .copy(engineCompressors = mapOf(2 to models))
        var model by mutableStateOf(AircraftAlertModel("test", parameters))
        val telemetry = TelemetryParser.parse(
            """{"valid":true,"H, m":1000,"TAS, km/h":0,"throttle 2, %":100,"compressor stage 2":1}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(telemetry, FlightMetrics()))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(400, 300, listOf(
            HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 400, 300,
                engineIndex = 2, fields = listOf("compressor")))))
        compose.setContent { MaterialTheme { Box(Modifier.size(400.dp, 300.dp)) {
            HudPanel(flight, settings, emptyList(), model) {}
        } } }
        fun update(throttle: Double, stage: Double) = compose.runOnIdle {
            flight = flight.copy(telemetry = telemetry.copy(engines = telemetry.engines.map {
                it.copy(throttlePercent = throttle, compressorStage = stage)
            }))
        }
        fun check(actual: Int, recommended: Int?) {
            val gauge = compose.onNodeWithTag("hud-compressor-stage-2")
            gauge.assertRangeInfoEquals(ProgressBarRangeInfo(actual.toFloat(), 1f..2f))
            if (recommended == null) {
                gauge.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
                compose.onNodeWithTag("hud-compressor-advice-2").assertDoesNotExist()
            } else {
                gauge.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    "模型建议 $recommended 档；圆点为当前档位，长线为建议档位"))
                compose.onNodeWithText("#2 增压器 $actual → $recommended（基础燃油、15°C 模型估算）").assertIsDisplayed()
            }
        }
        check(1, 2)
        update(110.0, 1.0) // WEP favours the opposite stage; the old suggestion is gone.
        check(1, null)
        update(110.0, 2.0)
        check(2, 1)
        compose.runOnIdle { model = model.copy(parameters = parameters.copy(
            engineCompressors = mapOf(2 to models.copy(wepStages = null)))) }
        check(2, null) // A missing WEP model must not fall back to military advice.
        compose.runOnIdle { model = AircraftAlertModel("test", parameters) }
        check(2, 1)
        update(110.0, 1.0) // Pilot reaches the suggested stage.
        check(1, null)
        update(100.0, 1.0)
        check(1, 2)
        update(99.0, 1.0)
        check(1, null)
    }

    @Test fun previewShowsFirstAndLastCompressorStagesWithoutInventingMissingValues() {
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("compressor"))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 300,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudLayoutPreview(settings, missing = missing)
        } } }
        fun checkStages() {
            compose.onNodeWithTag("hud-compressor-stage-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 1f..2f))
            compose.onNodeWithTag("hud-compressor-stage-2").assertRangeInfoEquals(ProgressBarRangeInfo(2f, 1f..2f))
        }
        checkStages()
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-advice-2").assertDoesNotExist()
        compose.runOnIdle { missing = true }
        compose.onNodeWithTag("hud-compressor-stage-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-stage-2").assertDoesNotExist()
        compose.onAllNodesWithText("— ").assertCountEquals(2)
        compose.runOnIdle { missing = false }
        checkStages()
    }

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
        var alerts by mutableStateOf(emptyList<FlightAlert>())
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("compressor"))
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false, hudMechanization = false,
            hudEngineIndex = 1, hudEngineFields = listOf("compressor"),
            hudSceneLayout = HudSceneLayout(800, 300, listOf(region, region.copy(id = "two", x = 400, engineIndex = 2)))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 400.dp)) {
            HudPanel(connection, settings, alerts, model) {}
        } } }
        compose.onNodeWithTag("hud-compressor-advice-1").assertIsDisplayed()
        compose.onNodeWithTag("hud-compressor-advice-2").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-stage-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 1f..2f))
        compose.onNodeWithTag("hud-compressor-stage-2").assertRangeInfoEquals(ProgressBarRangeInfo(2f, 1f..2f))
        compose.onNodeWithTag("hud-compressor-stage-1").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "模型建议 2 档；圆点为当前档位，长线为建议档位"))
        compose.onNodeWithTag("hud-compressor-stage-2").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        val recommendedPixels = compose.onNodeWithTag("hud-compressor-stage-1").captureToImage().toPixelMap()
        compose.onNodeWithText("1 号增压器 · 1 / 2 档 · 建议 2 档（长线）").assertIsDisplayed()
        compose.runOnIdle { connection = flight.copy(telemetry = telemetry.copy(tasKmh = null)) }
        compose.onNodeWithTag("hud-compressor-stage-1").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("hud-compressor-stage-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 1f..2f))
        val missingPixels = compose.onNodeWithTag("hud-compressor-stage-1").captureToImage().toPixelMap()
        compose.onNodeWithText("1 号增压器 · 1 / 2 档").assertIsDisplayed()
        assertEquals(recommendedPixels.width, missingPixels.width)
        assertEquals(recommendedPixels.height, missingPixels.height)
        var changed = 0
        for (y in 0 until recommendedPixels.height) for (x in 0 until recommendedPixels.width) {
            if (x < recommendedPixels.width / 2) assertEquals(recommendedPixels[x, y], missingPixels[x, y])
            else if (recommendedPixels[x, y] != missingPixels[x, y]) changed++
        }
        assertTrue(changed > 10, "The recommendation line must actually disappear from the second-stage position")
        compose.runOnIdle { connection = flight }
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(
            regions = settings.hudSceneLayout!!.regions.map { if (it.id == "one") it.copy(showEngineInstruments = false) else it })) }
        compose.onNodeWithTag("hud-compressor-stage-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-stage-2").assertExists()
        compose.onNodeWithText("#1 增压器 1 → 2（基础燃油、15°C 模型估算）").assertIsDisplayed()
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { alerts = listOf(FlightAlert.COMPRESSOR_STAGE) }
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.COMPRESSOR_STAGE.label))
        compose.onNodeWithText("2 ").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { alerts = listOf(FlightAlert.COMPRESSOR_STAGE) }
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithTag("hud-compressor-stage-2").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { model = model.copy(aircraft = "test"); settings = settings.copy(hudSceneLayout = null) }
        compose.onNodeWithTag("hud-compressor-advice-1").assertIsDisplayed()
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, FlightAlert.COMPRESSOR_STAGE.label))
        compose.runOnIdle { connection = ConnectionState.Flying(telemetry.copy(tasKmh = null), FlightMetrics()) }
        compose.onNodeWithTag("hud-compressor-stage-1").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 1f..2f))
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
        compose.onNodeWithText("1 ").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        for (stage in listOf(null, 0.0, 1.5, 3.0)) {
            compose.runOnIdle { connection = flight.copy(telemetry = telemetry.copy(engines = telemetry.engines.map {
                if (it.index == 1) it.copy(compressorStage = stage) else it
            })) }
            compose.onNodeWithTag("hud-compressor-stage-1").assertDoesNotExist()
        }
        compose.runOnIdle { connection = flight; settings = settings.copy(hudEngineFields = listOf("rpm")) }
        compose.onNodeWithTag("hud-compressor-stage-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-advice-1").assertDoesNotExist()
    }
}
