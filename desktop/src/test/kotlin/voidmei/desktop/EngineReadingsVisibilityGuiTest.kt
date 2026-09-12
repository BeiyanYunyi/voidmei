package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import voidmei.telemetry.HudEngineField
import kotlin.test.*

class EngineReadingsVisibilityGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hidingTableKeepsMixedInstrumentsAndMissingDataMessage() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 480, 600,
            fields = listOf("rpm", "throttle", "rpm_control", "radiator", "compressor"),
            engineControlsLayout = EngineControlsLayout.MIXED, showEngineReadings = false)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(960, 600, listOf(one,
            one.copy(id = "two", x = 480, engineIndex = 2, showEngineReadings = true))))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(960.dp, 600.dp)) { HudLayoutPreview(settings, missing = missing) } } }
        compose.onNodeWithText("2400 RPM").assertDoesNotExist()
        compose.onNodeWithText("1 号水散热器，满刻度 100% · 35%").assertExists()
        compose.onNodeWithText("2300 RPM").assertExists()
        compose.onNodeWithTag("hud-engine-radiator-1").assertRangeInfoEquals(ProgressBarRangeInfo(.35f, 0f..1f))
        compose.onNodeWithTag("hud-compressor-stage-1").assertExists()
        val out = Path.of("build/hud-preview/engine-readings-hidden.png")
        Files.createDirectories(out.parent)
        Files.write(out, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.runOnIdle { missing = true }
        compose.onNodeWithText("暂无所选发动机读数").assertExists()
        compose.onNodeWithTag("hud-engine-radiator-1").assertDoesNotExist()
    }
    @Test fun hiddenTablePreservesValidWarningsAndRemovesStaleWarnings() {
        var engine by mutableStateOf(hudPreviewFlight().telemetry.engines.first().copy(rpm = 500.0))
        compose.setContent { MaterialTheme { Column {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.RPM),
                warnings = mapOf(HudEngineField.RPM to "转速过低"), showReadings = false, showInstruments = false)
        } } }
        compose.onNodeWithText("转速：转速过低").assertExists()
        compose.onNodeWithText("500 RPM").assertDoesNotExist()
        compose.runOnIdle { engine = engine.copy(rpm = null) }
        compose.onNodeWithText("转速：转速过低").assertDoesNotExist()
        compose.onNodeWithText("暂无所选发动机读数").assertExists()
    }
    @Test fun horizontalInstrumentsStillShowExactValuesWithoutTable() {
        val engine = hudPreviewFlight().telemetry.engines.first()
        compose.setContent { MaterialTheme { Column {
            HudEnginePanel(listOf(engine), 1, fields = listOf(HudEngineField.THROTTLE, HudEngineField.MIXTURE), showReadings = false)
        } } }
        compose.onNodeWithText("油门：95 %").assertExists()
        compose.onNodeWithText("1 号混合比，满刻度 120% · 100%").assertExists()
    }
    @Test fun tableSwitchOnlyChangesChosenRegion() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 250)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two", x = 300))))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-engine-readings-one").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(one.copy(showEngineReadings = false), original.hudSceneLayout!!.regions[1]), settings.hudSceneLayout!!.regions) }
    }
}
