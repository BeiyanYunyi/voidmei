package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.fm.*
import voidmei.telemetry.*

class HudEnginePowerPercentGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewSuppliesIndependentPeakReferencesAndClearsMissingOutput() {
        val region = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("fm_power_percent"))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(800, 300,
            listOf(region, region.copy(id = "two", x = 400, engineIndex = 2))))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudLayoutPreview(settings, missing = missing)
        } } }
        fun checkValues() {
            compose.onNodeWithText("75.0 % · FM 功率峰值").assertIsDisplayed()
            compose.onNodeWithText("50.0 % · FM 功率峰值").assertIsDisplayed()
            compose.onNodeWithTag("hud-engine-fm_power_percent-1").assertRangeInfoEquals(ProgressBarRangeInfo(.75f, 0f..1f))
            compose.onNodeWithTag("hud-engine-fm_power_percent-2").assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        }
        checkValues()
        compose.runOnIdle { missing = true }
        compose.onAllNodesWithText("— %").assertCountEquals(2)
        compose.onNodeWithTag("hud-engine-fm_power_percent-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-fm_power_percent-2").assertDoesNotExist()
        compose.runOnIdle { missing = false }
        checkValues()
    }

    @Test fun regionsShowSeparateReferencesAndClearOnModelMismatch() {
        val parameters = FlightModelExtractor.extract(BlkParser.parse("Engine0 { Main { Type:t=Jet } } Engine1 { Main { Type:t=Inline } }"))
            .copy(enginePeaks = listOf(EnginePeakReference(1, EnginePeakKind.THRUST_KGF, 1000.0),
                EnginePeakReference(2, EnginePeakKind.SHAFT_POWER_HP, 2000.0)))
        var model by mutableStateOf(AircraftAlertModel("test", parameters))
        val flight = ConnectionState.Flying(TelemetryParser.parse(
            """{"valid":true,"thrust 1, kgs":400,"power 2, hp":1500}""",
            """{"valid":true,"type":"test"}""")!!, FlightMetrics())
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 400, 300, fields = listOf("fm_power_percent"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(800, 300,
            listOf(one, one.copy(id = "two", x = 400, engineIndex = 2)))))
        compose.setContent { MaterialTheme { Box(Modifier.size(800.dp, 300.dp)) {
            HudPanel(flight, settings, emptyList(), model) {}
        } } }
        compose.onNodeWithText("40.0 % · FM 推力峰值").assertIsDisplayed()
        compose.onNodeWithText("75.0 % · FM 功率峰值").assertIsDisplayed()
        compose.onNodeWithTag("hud-engine-fm_power_percent-1").assertRangeInfoEquals(ProgressBarRangeInfo(.4f, 0f..1f))
        compose.onNodeWithTag("hud-engine-fm_power_percent-2").assertRangeInfoEquals(ProgressBarRangeInfo(.75f, 0f..1f))
        compose.runOnIdle { settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.copy(
            regions = settings.hudSceneLayout!!.regions.map { if (it.id == "one") it.copy(showEngineInstruments = false) else it })) }
        compose.onNodeWithTag("hud-engine-fm_power_percent-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-engine-fm_power_percent-2").assertExists()
        compose.onNodeWithText("40.0 % · FM 推力峰值").assertIsDisplayed()
        compose.runOnIdle { model = model.copy(aircraft = "other") }
        compose.onNodeWithText("40.0 % · FM 推力峰值").assertDoesNotExist()
        compose.onNodeWithText("75.0 % · FM 功率峰值").assertDoesNotExist()
        compose.onAllNodesWithText("— %").assertCountEquals(2)
        compose.onNodeWithTag("hud-engine-fm_power_percent-2").assertDoesNotExist()
    }
}
