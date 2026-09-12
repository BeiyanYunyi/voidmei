package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import voidmei.config.*
import kotlin.test.*

class EngineControlDimensionsGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dimensionsApplyToBothDirectionsAndCompressorOnlyInsideChosenRegion() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 480, 600,
            fields = listOf("throttle", "radiator", "compressor"), showEngineReadings = false,
            engineControlsLayout = EngineControlsLayout.MIXED, engineControlDimensions = EngineControlDimensions(160, 16))
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(960, 600, listOf(one,
            one.copy(id = "two", x = 480, engineIndex = 2, engineControlDimensions = null))))
        var markerColor = Color.Unspecified
        compose.setContent { MaterialTheme {
            markerColor = MaterialTheme.colorScheme.primary
            Box(Modifier.size(960.dp, 600.dp)) { HudLayoutPreview(settings) }
        } }
        val v = compose.onNodeWithTag("hud-engine-throttle-1").getUnclippedBoundsInRoot()
        assertEquals(160.dp, v.bottom - v.top); assertEquals(16.dp, v.right - v.left)
        for (tag in listOf("hud-engine-radiator-1", "hud-compressor-stage-1")) {
            val h = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals(160.dp, h.right - h.left)
            if (tag == "hud-compressor-stage-1") assertEquals(16.dp, h.bottom - h.top)
            else {
                // Material's progress semantics extend vertically beyond its painted track.
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                val coloredRows = (0 until pixels.height).count { y ->
                    val color = pixels[pixels.width / 5, y]
                    kotlin.math.abs(color.red - markerColor.red) < .01f &&
                        kotlin.math.abs(color.green - markerColor.green) < .01f &&
                        kotlin.math.abs(color.blue - markerColor.blue) < .01f
                }
                assertEquals(compose.onNodeWithTag("hud-engine-throttle-1").captureToImage().width, coloredRows)
            }
        }
        val other = compose.onNodeWithTag("hud-engine-radiator-2").getUnclippedBoundsInRoot()
        assertTrue(other.right - other.left > 160.dp)
        val output = Path.of("build/hud-preview/engine-control-dimensions.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(compose.onNodeWithTag("hud-scene")
            .captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
    }
    @Test fun inputsApplyTogetherAndInvalidDraftDoesNotChangeRegion() {
        val original = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 300, 250)
        var region by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 650.dp).verticalScroll(rememberScrollState())) {
            EngineControlDimensionsSettings(region) { region = it }
        } } }
        compose.onNodeWithTag("engine-dimensions-engine").performClick()
        compose.onNodeWithTag("engine-length-engine").performTextReplacement("200")
        compose.onNodeWithTag("engine-thickness-engine").performTextReplacement("bad")
        compose.onNodeWithTag("engine-dimensions-apply-engine").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(original, region) }
        compose.onNodeWithTag("engine-thickness-engine").performTextReplacement("20")
        compose.onNodeWithTag("engine-dimensions-apply-engine").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original.copy(engineControlDimensions = EngineControlDimensions(200, 20)), region) }
        compose.onNodeWithTag("engine-length-engine").performTextReplacement("999")
        compose.onNodeWithTag("engine-dimensions-apply-engine").assertIsNotEnabled()
        compose.onNodeWithTag("engine-dimensions-reset-engine").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, region) }
    }
}
