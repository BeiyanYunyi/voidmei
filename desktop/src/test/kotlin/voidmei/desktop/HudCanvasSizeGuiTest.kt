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
import kotlin.test.*

class HudCanvasSizeGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dimensionsApplyTogetherAndPreserveRegionEditsMadeWhileTyping() {
        var scene by mutableStateOf(HudSceneLayout(800, 600, listOf(
            HudRegion("edge", HudRegionContent.ENGINE, 600, 400, 200, 200, engineIndex = 2))))
        compose.setContent { MaterialTheme { Column(Modifier.size(600.dp, 600.dp)) {
            HudCanvasSizeSettings(scene) { scene = it }
        } } }
        compose.onNodeWithTag("hud-canvas-apply").assertIsNotEnabled()
        compose.onNodeWithTag("hud-canvas-width").performTextReplacement("0")
        compose.onNodeWithTag("hud-canvas-apply").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(800, scene.width) }
        compose.onNodeWithTag("hud-canvas-width").performTextReplacement("400")
        compose.onNodeWithTag("hud-canvas-height").performTextReplacement("300")
        compose.runOnIdle {
            assertEquals(600, scene.height)
            scene = scene.copy(regions = scene.regions.map { it.copy(backgroundAlpha = .25f) })
        }
        compose.onNodeWithTag("hud-canvas-apply").performClick()
        compose.runOnIdle {
            assertEquals(400, scene.width); assertEquals(300, scene.height)
            assertEquals(200, scene.regions.single().x); assertEquals(100, scene.regions.single().y)
            assertEquals(.25f, scene.regions.single().backgroundAlpha)
            scene = SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout!!
        }
        compose.onNodeWithTag("hud-canvas-apply").assertIsNotEnabled()
        compose.onNodeWithTag("hud-canvas-width").performTextReplacement("1920")
        compose.onNodeWithTag("hud-canvas-height").performTextReplacement("1080")
        compose.onNodeWithTag("hud-canvas-apply").performClick()
        compose.runOnIdle {
            assertEquals(1920, scene.width); assertEquals(1080, scene.height)
            assertEquals(200, scene.regions.single().width)
            assertEquals(200, scene.regions.single().x)
        }
    }
}
