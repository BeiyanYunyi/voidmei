package voidmei.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState

/** The overlay accepts pointer interaction without becoming the keyboard focus window. */
@Composable
internal fun HudWindow(
    state: WindowState,
    onCloseRequest: () -> Unit,
    compatibilityMode: Boolean = false,
    clickThrough: Boolean = false,
    onPointerError: (String?) -> Unit = {},
    visible: Boolean = true,
    content: @Composable WindowScope.() -> Unit,
) {
    if (java.lang.Boolean.getBoolean("voidmei.diagnostics.hud.swingGraphics")) {
        SwingGraphicsHudProbe(onCloseRequest)
        return
    }
    if (compatibilityMode || java.lang.Boolean.getBoolean("voidmei.hud.swingGraphics")) {
        SwingGraphicsHudWindow(state, onCloseRequest, visible = visible) {
            HudPointerInput(window, clickThrough, onPointerError)
            content()
        }
        return
    }
    Window(onCloseRequest = onCloseRequest, title = "VoidMei HUD", state = state, visible = visible,
        undecorated = true, transparent = !java.lang.Boolean.getBoolean("voidmei.diagnostics.hud.opaque"), alwaysOnTop = true,
        resizable = false, focusable = java.lang.Boolean.getBoolean("voidmei.diagnostics.hud.focusable")) {
        HudPointerInput(window, clickThrough, onPointerError)
        if (java.lang.Boolean.getBoolean("voidmei.diagnostics.hud.minimal")) {
            rememberRendererDiagnostics(window)
            androidx.compose.material3.MaterialTheme {
                androidx.compose.material3.Text("VoidMei transparent GPU probe")
            }
        } else content()
    }
}
