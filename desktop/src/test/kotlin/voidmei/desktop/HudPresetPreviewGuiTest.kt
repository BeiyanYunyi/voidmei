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

    @Test fun switchingPresetsReusesReadonlyWindowWithoutChangingEditablePreview() {
        fun scene(id: String, field: String) = HudSceneLayout(400, 300,
            listOf(HudRegion(id, HudRegionContent.FLIGHT, 0, 0, 400, 300, fields = listOf(field))))
        val original = AppSettings(hudEnabled = false, hudSceneLayout = scene("active", "ias"),
            hudScenePresets = mapOf("altitude" to scene("saved-altitude", "altitude"), "fuel" to scene("saved-fuel", "fuel")))
        var settings by mutableStateOf(original)
        compose.setContent { MaterialTheme { Column { HudSettingsPanel(settings) { settings = it } } } }
        fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        fun window(title: String) = Window.getWindows().filterIsInstance<Frame>().singleOrNull {
            it.isDisplayable && it.title == title
        }
        click("hud-layout-preview")
        compose.waitUntil(5000) { window("HUD 布局预览 · 示例数据")?.isVisible == true }
        val editable = window("HUD 布局预览 · 示例数据")!!
        click("hud-presets-toggle")
        click("hud-preset-preview-altitude")
        compose.waitUntil(5000) { window("HUD 布局预览 · altitude · 示例数据")?.isVisible == true }
        compose.waitUntil(5000) { compose.onAllNodesWithText("1500 m").fetchSemanticsNodes().size == 1 }
        val readonly = window("HUD 布局预览 · altitude · 示例数据")!!
        compose.onAllNodesWithText("340 km/h").assertCountEquals(1)
        compose.onAllNodesWithTag("hud-preview-edit-regions").assertCountEquals(1)
        click("hud-preset-preview-fuel")
        compose.waitUntil(5000) { window("HUD 布局预览 · fuel · 示例数据") === readonly &&
            compose.onAllNodesWithText("300 kg").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("1500 m").assertDoesNotExist()
        compose.onAllNodesWithText("340 km/h").assertCountEquals(1)
        compose.runOnIdle {
            assertEquals(original, settings)
            assertSame(editable, window("HUD 布局预览 · 示例数据"))
            readonly.dispatchEvent(WindowEvent(readonly, WindowEvent.WINDOW_CLOSING))
        }
        compose.waitUntil(5000) { !readonly.isDisplayable }
        compose.onNodeWithText("340 km/h").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(editable.isDisplayable)
            assertEquals(original, settings)
            editable.dispatchEvent(WindowEvent(editable, WindowEvent.WINDOW_CLOSING))
        }
        compose.waitUntil(5000) { !editable.isDisplayable }
    }

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
