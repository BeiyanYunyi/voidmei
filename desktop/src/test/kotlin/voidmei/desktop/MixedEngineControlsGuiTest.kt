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
import kotlin.test.*

class MixedEngineControlsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun legacyFieldDirectionsAreMixedWithoutDuplicatingThrottleOrChangingRanges() {
        val fields = listOf("throttle", "rpm_control", "fm_power_percent", "mixture", "radiator", "oil_radiator", "compressor")
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 480, 600,
            fields = fields, engineControlsLayout = EngineControlsLayout.MIXED)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(960, 600,
            listOf(one, one.copy(id = "two", x = 480, engineIndex = 2, engineControlsLayout = EngineControlsLayout.HORIZONTAL))))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(960.dp, 600.dp)) { HudLayoutPreview(settings, missing = missing) } } }
        for (field in fields - "compressor") {
            val tag = "hud-engine-$field-1"
            compose.onAllNodesWithTag(tag).assertCountEquals(1)
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val vertical = field in listOf("throttle", "rpm_control", "fm_power_percent")
            assertEquals(vertical, bounds.bottom - bounds.top > bounds.right - bounds.left)
        }
        compose.onNodeWithTag("hud-engine-mixture-1").assertRangeInfoEquals(ProgressBarRangeInfo(100f / 120f, 0f..1f))
        compose.onNodeWithTag("hud-engine-radiator-1").assertRangeInfoEquals(ProgressBarRangeInfo(.35f, 0f..1f))
        compose.onNodeWithTag("hud-compressor-stage-1").assertExists()
        val output = Path.of("build/hud-preview/mixed-engine-controls.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.runOnIdle { missing = true }
        for (field in fields - "compressor") compose.onNodeWithTag("hud-engine-$field-1").assertDoesNotExist()
        compose.onNodeWithTag("hud-compressor-stage-1").assertDoesNotExist()
    }
    @Test fun mixedChoiceIsExclusiveAndOnlyChangesItsRegion() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 250, engineControlsLayout = EngineControlsLayout.VERTICAL)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two", x = 300))))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-engine-mixed-one").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-engine-mixed-one").assertIsSelected()
        compose.onNodeWithTag("hud-region-engine-vertical-one").assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf(one.copy(engineControlsLayout = EngineControlsLayout.MIXED),
            original.hudSceneLayout!!.regions[1]), settings.hudSceneLayout!!.regions) }
    }
}
