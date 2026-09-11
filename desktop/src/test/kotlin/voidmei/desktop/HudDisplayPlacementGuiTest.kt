package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import java.awt.Window
import voidmei.config.*
import kotlin.test.*

class HudDisplayPlacementGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compatibleHudCoversDisplayAndReturnsToCanvasWithoutAnotherWindow() {
        val target = hudDisplays().first()
        var scene by mutableStateOf(HudSceneLayout(400, 250, listOf(HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 300, 200))))
        var native: Window? = null
        compose.setContent {
            val state = rememberWindowState(width = 400.dp, height = 250.dp)
            MaterialTheme { Column { HudDisplaySettings(scene) { scene = it } } }
            HudWindow(state, {}, compatibilityMode = true, clickThrough = true) {
                SideEffect { native = window }
                updateHudWindowSize(window, state, 440, 200.dp, scene)
                MaterialTheme { Text("Display placement probe") }
            }
        }
        compose.waitUntil(5000) { native?.isShowing == true }
        compose.onNodeWithTag("hud-display-0").performClick()
        compose.waitUntil(5000) { native?.bounds == target.bounds }
        val original = assertNotNull(native)
        compose.runOnIdle { scene = scene.copy(displayId = "disconnected-test-display") }
        val primary = hudDisplays().first { it.primary }
        compose.waitUntil(5000) { native?.bounds == primary.bounds }
        compose.onNodeWithTag("hud-display-canvas").performClick()
        compose.waitUntil(5000) { native?.width == 400 && native?.height == 250 }
        val settled = System.nanoTime()
        compose.waitUntil(5000) { System.nanoTime() - settled > 1_200_000_000L }
        compose.runOnIdle {
            assertSame(original, native)
            assertEquals(400, original.width); assertEquals(250, original.height)
            assertEquals(1, Window.getWindows().filterIsInstance<java.awt.Frame>().count { it.isDisplayable && it.title == "VoidMei HUD" })
        }
        compose.setContent {}
        compose.runOnIdle { assertFalse(original.isDisplayable) }
    }
}
