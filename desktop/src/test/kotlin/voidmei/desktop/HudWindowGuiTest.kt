package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class HudWindowGuiTest {
    @Test fun transientHudHeightChangesDoNotRepeatedlyResizeCompatibleWindow() {
        var height by mutableStateOf(220.dp)
        var native: java.awt.Window? = null
        compose.setContent {
            val state = rememberWindowState(width = 320.dp, height = 220.dp)
            HudWindow(state, {}, compatibilityMode = true) {
                updateHudWindowSize(window, state, 320, height)
                SideEffect { native = window }
                Text("HUD 尺寸稳定性")
            }
        }
        compose.waitUntil(5000) { native?.isVisible == true }
        compose.runOnIdle { height = 300.dp }
        compose.waitUntil(5000) { native?.height == 300 }
        val frame = assertNotNull(native)
        val observed = mutableListOf<Int>()
        repeat(8) { i ->
            compose.runOnIdle { height = if (i % 2 == 0) 220.dp else 300.dp }
            Thread.sleep(100)
            compose.runOnIdle { assertSame(frame, native); assertTrue(frame.isVisible); observed += frame.height }
        }
        assertTrue(observed.all { it == 300 }, "Transient readings resized native HUD: $observed")
        compose.runOnIdle { height = 220.dp }
        compose.waitUntil(5000) { frame.height == 220 }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun composeHudCanStartHiddenAndRestoreTheSameWindow() = checkInitiallyHidden(false)

    @Test fun compatibleHudCanStartHiddenAndRestoreTheSameWindow() = checkInitiallyHidden(true)

    private fun checkInitiallyHidden(compatible: Boolean) {
        var shown by mutableStateOf(false)
        var native: java.awt.Window? = null
        val previousWindows = java.awt.Window.getWindows().toSet()
        compose.setContent {
            HudWindow(rememberWindowState(width = 320.dp, height = 160.dp), {},
                compatibilityMode = compatible, visible = shown) {
                SideEffect { native = window }
                Text("隐藏后恢复测试")
            }
        }
        compose.runOnIdle {
            assertFalse(java.awt.Window.getWindows().any { it !in previousWindows &&
                it is java.awt.Frame && it.title == "VoidMei HUD" && it.isVisible })
            shown = true
        }
        compose.waitUntil(5000) { native?.isVisible == true }
        val first = assertNotNull(native)
        val bounds = first.bounds
        compose.runOnIdle { shown = false }
        compose.waitUntil(5000) { !first.isVisible }
        assertTrue(first.isDisplayable)
        compose.runOnIdle { shown = true }
        compose.waitUntil(5000) { first.isVisible }
        assertSame(first, native)
        assertEquals(bounds, first.bounds)
    }

    @Test fun compatibilityControlUpdatesSettingsWithoutChangingLayoutPreferences() {
        var settings by mutableStateOf(voidmei.config.AppSettings(hudWidthDp = 680, hudFontScale = 1.5f))
        compose.setContent {
            androidx.compose.foundation.layout.Column { HudSettingsPanel(settings) { settings = it } }
        }
        compose.onNodeWithTag("hud-compatibility").performClick()
        compose.runOnIdle {
            assertTrue(settings.hudCompatibilityMode)
            assertEquals(680, settings.hudWidthDp)
            assertEquals(1.5f, settings.hudFontScale)
        }
        compose.onNodeWithTag("hud-compatibility").performClick()
        compose.runOnIdle { assertFalse(settings.hudCompatibilityMode) }
    }

    @Test fun switchingPresentationRecreatesOnlyTheWindowAndRetainsGeometry() {
        var compatibility by mutableStateOf(false)
        var native: java.awt.Window? = null
        compose.setContent {
            val state = rememberWindowState(width = 320.dp, height = 160.dp,
                position = androidx.compose.ui.window.WindowPosition.Absolute(70.dp, 80.dp))
            HudWindow(state, {}, compatibilityMode = compatibility) {
                SideEffect { native = window }
                Text("显示模式切换测试")
            }
        }
        compose.waitUntil(5000) { native?.isVisible == true }
        var previous = assertNotNull(native)
        for (mode in listOf(true, false, true)) {
            val bounds = previous.bounds
            compose.runOnIdle { compatibility = mode }
            compose.waitUntil(5000) { native !== previous && native?.isVisible == true && !previous.isDisplayable }
            val current = assertNotNull(native)
            compose.waitUntil(5000) { current.bounds == bounds }
            compose.runOnIdle {
                assertFalse(current.isFocusableWindow)
                assertEquals(!mode, current is androidx.compose.ui.awt.ComposeWindow)
            }
            previous = current
        }
    }

    @Test fun overlayCannotTakeKeyboardFocusAndKeepsPolicyWhenReopened() {
        var visible by mutableStateOf(true)
        var native: java.awt.Window? = null
        compose.setContent {
            if (visible) HudWindow(rememberWindowState(width = 200.dp, height = 100.dp), {}) {
                SideEffect { native = window }
                TextButton(onClick = { visible = false }, modifier = Modifier.fillMaxSize()) { Text("关闭 HUD 焦点测试") }
            }
        }
        compose.waitUntil(5000) { native?.isVisible == true }
        val first = assertNotNull(native)
        compose.runOnIdle {
            assertFalse(first.focusableWindowState)
            assertFalse(first.isFocusableWindow)
            first.requestFocus()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(first.isFocused)
        }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.waitUntil(5000) { !first.isDisplayable }
        compose.runOnIdle { native = null; visible = true }
        compose.waitForIdle()
        compose.waitUntil(5000) { native?.isVisible == true }
        compose.runOnIdle {
            val reopened = assertNotNull(native)
            assertNotSame(first, reopened)
            assertFalse(reopened.isFocusableWindow)
            assertFalse(reopened.isFocused)
            visible = false
        }
    }
}
