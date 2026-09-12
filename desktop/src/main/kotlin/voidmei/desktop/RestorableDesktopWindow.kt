package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState

/** Keep restore requests outside the window's composition, which pauses while hidden. */
@Composable
internal fun RestorableDesktopWindow(
    onCloseRequest: () -> Unit,
    title: String,
    state: WindowState,
    visible: Boolean,
    restoreRequest: Long,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    var nativeWindow by remember { mutableStateOf<ComposeWindow?>(null) }
    LaunchedEffect(visible, restoreRequest, nativeWindow) {
        if (visible && restoreRequest > 0) nativeWindow?.let(::restoreDesktopWindow)
    }
    Window(onCloseRequest = onCloseRequest, title = title, state = state, visible = visible) {
        DisposableEffect(window) {
            nativeWindow = window
            onDispose { nativeWindow = null }
        }
        content()
    }
}
