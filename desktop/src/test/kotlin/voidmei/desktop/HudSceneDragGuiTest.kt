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

    @Test fun explicitTargetMovesCoveredRegionWithoutReorderingAndAutoRestoresTopHit() {
        val bottom = HudRegion("bottom", HudRegionContent.FLIGHT, 100, 100, 300, 200)
        val top = bottom.copy(id = "top")
        var scene by mutableStateOf(HudSceneLayout(1000, 600, listOf(bottom, top)))
        var target by mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { Column {
            HudDragTargetSettings(scene.regions, target) { target = it }
            Box(Modifier.size(500.dp, 300.dp)) {
                HudSceneDragOverlay(scene, { id, x, y -> scene = scene.moveRegion(id, x, y) }, targetId = target)
            }
        } } }
        compose.onNodeWithTag("hud-drag-target-menu").performClick()
        compose.onNodeWithTag("hud-drag-target-bottom").performClick()
        val overlay = compose.onNodeWithTag("hud-scene-drag-overlay")
        overlay.performTouchInput { swipe(Offset(100f, 100f), Offset(150f, 125f), 500) }
        compose.runOnIdle { assertEquals(listOf(bottom.copy(x = 200, y = 150), top), scene.regions) }
        // Outside the designated region: leave the top region untouched.
        overlay.performTouchInput { swipe(Offset(60f, 60f), Offset(80f, 70f), 500) }
        compose.runOnIdle { assertEquals(top, scene.regions.last()) }
        compose.onNodeWithTag("hud-drag-target-menu").performClick()
        compose.onNodeWithTag("hud-drag-target-auto").performClick()
        overlay.performTouchInput { swipe(Offset(125f, 100f), Offset(175f, 125f), 500) }
        compose.runOnIdle { assertEquals(listOf(bottom.copy(x = 200, y = 150), top.copy(x = 200, y = 150)), scene.regions) }
    }

    @Test fun cornerDragResizesAtPreviewScaleAndClampsBothSizeLimits() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 100, 100, 300, 200, 0f, fields = listOf("ias"))
        var settings by mutableStateOf(AppSettings(hudSceneLayout = HudSceneLayout(1000, 600, listOf(region))))
        compose.setContent { MaterialTheme { Box(Modifier.size(500.dp, 300.dp)) {
            HudLayoutPreview(settings, onRegionMove = { id, x, y ->
                settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.moveRegion(id, x, y))
            }, onRegionResize = { id, w, h ->
                settings = settings.copy(hudSceneLayout = settings.hudSceneLayout!!.resizeRegion(id, w, h))
            })
        } } }
        val overlay = compose.onNodeWithTag("hud-scene-drag-overlay")
        compose.onNodeWithTag("hud-resize-region-one").assertIsDisplayed()
        overlay.performTouchInput { swipe(Offset(195f, 145f), Offset(295f, 195f), 500) }
        compose.runOnIdle { assertEquals(region.copy(width = 500, height = 300), settings.hudSceneLayout!!.regions.single()) }
        overlay.performTouchInput { swipe(Offset(295f, 195f), Offset(10f, 10f), 500) }
        compose.runOnIdle { assertEquals(region.copy(width = 80, height = 40), settings.hudSceneLayout!!.regions.single()) }
        overlay.performTouchInput { swipe(Offset(88f, 68f), Offset(499f, 299f), 500) }
        compose.runOnIdle { assertEquals(region.copy(width = 900, height = 500), settings.hudSceneLayout!!.regions.single()) }
    }

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
        val handle = compose.onNodeWithTag("hud-resize-region-test").fetchSemanticsNode().boundsInRoot
        val start = handle.center - overlay.fetchSemanticsNode().boundsInRoot.topLeft
        overlay.performTouchInput { swipe(start, start + Offset(40f * scale, 20f * scale), 500) }
        compose.waitUntil(5000) { settings.hudSceneLayout!!.regions.single().width == 340 }
        compose.runOnIdle {
            assertEquals(220, settings.hudSceneLayout!!.regions.single().height)
            assertEquals(200, settings.hudSceneLayout!!.regions.single().x)
            assertEquals(150, settings.hudSceneLayout!!.regions.single().y)
        }
        compose.onNodeWithTag("hud-preview-edit-regions").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
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
