package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class HudSceneDragGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsPreviewCanEnableDraggingAndSaveTheNewPosition() {
        val region = HudRegion("test", HudRegionContent.FLIGHT, 100, 100, 300, 200, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(region))))
        compose.setContent { MaterialTheme { Column { HudSettingsPanel(settings) { settings = it } } } }
        compose.onNodeWithTag("hud-layout-preview").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-preview-edit-regions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hud-preview-edit-regions").performClick()
        val overlay = compose.onNodeWithTag("hud-scene-drag-overlay")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-scene-drag-overlay").fetchSemanticsNodes().isNotEmpty() }
        val bounds = overlay.fetchSemanticsNode().boundsInRoot
        val scale = minOf(bounds.width / 1000, bounds.height / 600, 1f)
        overlay.performTouchInput { swipe(Offset(200f * scale, 200f * scale), Offset(300f * scale, 250f * scale), 500) }
        compose.waitUntil(5000) { settings.hudSceneLayout!!.regions.single().x == 200 }
        compose.runOnIdle {
            assertEquals(150, settings.hudSceneLayout!!.regions.single().y)
            assertFalse(settings.hudEnabled)
            assertFalse(settings.hudClickThrough)
        }
        compose.onNodeWithTag("hud-preview-edit-regions").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("hud-scene-drag-overlay").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun previewDragMovesTopRegionAtHalfScaleAndKeepsLivePreferences() {
        val bottom = HudRegion("bottom", HudRegionContent.FLIGHT, 100, 100, 300, 200, fields = listOf("ias"))
        val top = bottom.copy(id = "top", backgroundAlpha = 0f, contentAlpha = 0f)
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(bottom, top)), hudClickThrough = true))
        var editing by mutableStateOf(true)
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 300.dp)) {
            HudLayoutPreview(settings, onRegionMove = if (editing) { id, x, y ->
                settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.moveRegion(id, x, y))
            } else null)
        } } }
        val node = compose.onNodeWithTag("hud-scene-drag-overlay")
        node.performTouchInput { swipe(Offset(100f, 100f), Offset(200f, 150f), 500) }
        compose.runOnIdle {
            val moved = settings.hudSceneLayout!!.regions.last()
            assertEquals(300, moved.x, "Preview pixels must convert to twice as many canvas units")
            assertEquals(200, moved.y)
            assertEquals(bottom, settings.hudSceneLayout!!.regions.first())
            assertEquals(top.copy(x = moved.x, y = moved.y), moved)
            assertTrue(settings.hudClickThrough)
        }
        node.performTouchInput { swipe(Offset(200f, 150f), Offset(490f, 290f), 500) }
        compose.runOnIdle {
            assertEquals(700, settings.hudSceneLayout!!.regions.last().x)
            assertEquals(400, settings.hudSceneLayout!!.regions.last().y)
        }
        node.performTouchInput { swipe(Offset(10f, 10f), Offset(40f, 30f), 200) }
        compose.runOnIdle {
            assertEquals(bottom, settings.hudSceneLayout!!.regions.first())
            assertEquals(700, settings.hudSceneLayout!!.regions.last().x)
            settings = SettingsJson.decode(SettingsJson.encode(settings))
            editing = false
        }
        compose.onNodeWithTag("hud-scene-drag-overlay").assertDoesNotExist()
    }
}
