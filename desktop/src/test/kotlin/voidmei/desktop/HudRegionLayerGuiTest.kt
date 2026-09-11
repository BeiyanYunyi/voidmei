package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class HudRegionLayerGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuLayerChangeRevealsCoveredReadingAndPreservesItsGeometry() {
        val speed = HudRegion("speed", HudRegionContent.FLIGHT, 0, 0, 300, 180, 1f, fields = listOf("ias"))
        val cover = speed.copy(id = "cover", fields = emptyList(), contentAlpha = 0f)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(320, 200, listOf(speed, cover)), hudValueColor = "#FFFFFF"))
        compose.setContent { MaterialTheme { Row(Modifier.size(1000.dp, 600.dp)) {
            Column(Modifier.width(600.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                HudSceneSettings(settings) { settings = it }
            }
            Box(Modifier.size(320.dp, 200.dp)) { HudPanel(hudPreviewFlight(), settings, emptyList(), null) {} }
        } } }
        fun whitePixels(): Int {
            val pixels = compose.onNodeWithText("340 km/h").captureToImage().toPixelMap()
            return (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x ->
                val c = pixels[x, y]; c.red > .9f && c.green > .9f && c.blue > .9f
            } }
        }
        assertEquals(0, whitePixels(), "The opaque region should cover the speed reading")
        compose.onNodeWithText("调整分区位置与透明度").performClick()
        compose.onNodeWithTag("hud-region-layer-down-speed").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("hud-region-layer-up-speed").performClick()
        assertTrue(whitePixels() > 10, "Moving speed forward must reveal its actual pixels")
        compose.runOnIdle {
            assertEquals(listOf(cover, speed), settings.hudSceneLayout!!.regions)
            settings = SettingsJson.decode(SettingsJson.encode(settings))
        }
        compose.onNodeWithTag("hud-region-layer-up-speed").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("hud-region-layer-down-speed").performClick()
        assertEquals(0, whitePixels())
        compose.runOnIdle { assertEquals(listOf(speed, cover), settings.hudSceneLayout!!.regions) }
    }
}
