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

class VerticalEngineControlsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun perRegionVerticalControlsKeepRangesAndDisappearForMissingData() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 480, 600,
            fields = listOf("throttle", "mixture", "radiator"), engineControlsLayout = EngineControlsLayout.VERTICAL)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(960, 600,
            listOf(one, one.copy(id = "two", x = 480, engineIndex = 2, engineControlsLayout = EngineControlsLayout.HORIZONTAL))))
        var missing by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.size(960.dp, 600.dp)) { HudLayoutPreview(settings, missing = missing) } } }
        val vertical = compose.onNodeWithTag("hud-engine-radiator-1")
        vertical.assertRangeInfoEquals(ProgressBarRangeInfo(.35f, 0f..1f))
        compose.onNodeWithTag("hud-engine-mixture-1").assertRangeInfoEquals(ProgressBarRangeInfo(100f / 120f, 0f..1f))
        compose.onNodeWithTag("hud-engine-throttle-1").assertRangeInfoEquals(ProgressBarRangeInfo(95f / 110f, 0f..1f))
        val v = vertical.getUnclippedBoundsInRoot()
        val h = compose.onNodeWithTag("hud-engine-radiator-2").getUnclippedBoundsInRoot()
        assertTrue(v.bottom - v.top > v.right - v.left)
        assertTrue(h.right - h.left > h.bottom - h.top)
        val output = Path.of("build/hud-preview/vertical-engine-controls.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        compose.runOnIdle { missing = true }
        for (index in 1..2) for (field in listOf("throttle", "mixture", "radiator"))
            compose.onNodeWithTag("hud-engine-$field-$index").assertDoesNotExist()
    }
    @Test fun settingsToggleOnlyChangesSelectedRegionAndKeepsChoiceWhenInstrumentsAreHidden() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 250)
        val original = AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two", x = 300))))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 650.dp).verticalScroll(rememberScrollState())) {
            HudSceneSettings(settings) { settings = it }
        } } }
        compose.expandHudRegionEditors()
        compose.onNodeWithTag("hud-region-engine-vertical-one").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(one.copy(engineControlsLayout = EngineControlsLayout.VERTICAL), original.hudSceneLayout!!.regions[1]), settings.hudSceneLayout!!.regions)
        }
        compose.onNodeWithTag("hud-region-engine-instruments-one").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-engine-vertical-one").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(EngineControlsLayout.VERTICAL, settings.hudSceneLayout!!.regions.first().engineControlsLayout) }
    }
}
