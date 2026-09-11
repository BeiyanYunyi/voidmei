package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowEvent
import kotlin.test.*
import voidmei.config.*

class HudPresetPreviewGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewIsReadOnlyAndDoesNotLoadSavedLayout() {
        val scene = HudSceneLayout(400, 300, listOf(HudRegion("saved", HudRegionContent.FLIGHT,
            0, 0, 400, 300, fields = listOf("ias"))), enabled = false)
        val original = AppSettings(hudEnabled = false, hudScenePresets = mapOf("saved" to scene))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column { HudScenePresetSettings(settings) { settings = it } } } }
        compose.onNodeWithTag("hud-presets-toggle").performClick()
        compose.onNodeWithTag("hud-preset-preview-saved").performSemanticsAction(SemanticsActions.OnClick) { it() }
        fun windows() = Window.getWindows().filterIsInstance<Frame>().filter {
            it.isDisplayable && it.title == "HUD 布局预览 · saved · 示例数据"
        }
        compose.waitUntil(5000) { windows().singleOrNull()?.isVisible == true }
        compose.waitUntil(5000) { compose.onAllNodesWithText("340 km/h").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("预设预览：saved（只读）").assertExists()
        compose.onNodeWithTag("hud-preview-edit-regions").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original, settings) }
        val frame = windows().single()
        compose.onNodeWithTag("hud-preset-preview-saved").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertSame(frame, windows().single()); assertEquals(original, settings) }
        compose.onNodeWithText("缺失数据").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("340 km/h").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) }
        compose.waitUntil(5000) { windows().isEmpty() }
        compose.runOnIdle { assertEquals(original, settings) }
    }
}
