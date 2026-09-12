package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import voidmei.telemetry.*
import kotlin.test.*

class EngineAircraftFuelGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun aircraftFuelKeepsPrecisionWarningsAndMissingDataIndependentOfEngine() {
        var percent by mutableStateOf<Double?>(37.26)
        var show by mutableStateOf(true)
        var alerts by mutableStateOf(emptyList<FlightAlert>())
        compose.setContent { MaterialTheme { Column(Modifier.width(400.dp)) {
            val flight = hudPreviewFlight()
            HudEnginePanel(emptyList(), 99, showReadings = false)
            EngineAircraftFuelPanel(flight.copy(metrics = flight.metrics.copy(fuelPercent = percent)), alerts, show)
        } } }
        compose.onNodeWithText("此编号无可用发动机数据").assertExists()
        compose.onNodeWithText("37.3 %").assertExists()
        val bar = compose.onNodeWithTag("hud-engine-aircraft-fuel")
        bar.assertRangeInfoEquals(ProgressBarRangeInfo(.3726f, 0f..1f))
        compose.runOnIdle { percent = 0.0; alerts = listOf(FlightAlert.EMPTY_FUEL) }
        bar.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        bar.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "整机燃油余量 0.0%，${FlightAlert.EMPTY_FUEL.label}"))
        compose.runOnIdle { show = false }
        bar.assertDoesNotExist()
        compose.onNodeWithText("0.0 %").assertExists()
        for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            compose.runOnIdle { percent = invalid; show = true }
            bar.assertDoesNotExist()
            compose.onNodeWithText("缺少有效整机燃油余量或容量").assertExists()
        }
    }
    @Test fun switchChangesOnlyChosenRegionAndSceneRendersFuelWithoutTable() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 400, showEngineReadings = false)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(800, 600, listOf(one, one.copy(id = "two", x = 300))))
        var settings by mutableStateOf(original)
        var preview by mutableStateOf(false)
        compose.setContent { MaterialTheme { Column(Modifier.size(800.dp, 700.dp).verticalScroll(rememberScrollState())) {
            if (preview) HudLayoutPreview(settings) else HudSceneSettings(settings) { settings = it }
        } } }
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-aircraft-fuel-one").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(one.copy(showAircraftFuel = true), original.hudSceneLayout!!.regions[1]), settings.hudSceneLayout!!.regions)
            preview = true
        }
        compose.onAllNodesWithTag("hud-engine-aircraft-fuel").assertCountEquals(1)
        compose.runOnIdle { settings = original }
        compose.onNodeWithTag("hud-engine-aircraft-fuel").assertDoesNotExist()
    }
}
