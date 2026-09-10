package voidmei.desktop

import androidx.compose.runtime.*
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

@Composable
internal fun HudPointerInput(window: Window, enabled: Boolean, onError: (String?) -> Unit) {
    val report by rememberUpdatedState(onError)
    val controller = remember(window) {
        val windows = WindowsPointerRegion(window)
        HudPointerController({
            when {
                com.sun.jna.Platform.isWindows() -> windows.setClickThrough(it)
                com.sun.jna.Platform.isLinux() -> X11PointerRegion.setClickThrough(window, it)
                else -> error("当前平台尚不支持 HUD 鼠标穿透")
            }
        }, { report(it) })
    }
    DisposableEffect(window, enabled) {
        var applied = false
        fun applyWhenVisible() {
            if (enabled && !applied && window.isShowing) applied = controller.set(true)
        }
        val listener = HierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) applyWhenVisible()
        }
        window.addHierarchyListener(listener)
        if (!enabled && controller.needsRestore && window.isDisplayable) controller.set(false)
        applyWhenVisible()
        onDispose {
            window.removeHierarchyListener(listener)
            if (controller.needsRestore && window.isDisplayable) controller.set(false)
        }
    }
}
