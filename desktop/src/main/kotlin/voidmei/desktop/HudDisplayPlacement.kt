package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import javax.swing.Timer

internal data class HudDisplay(val id: String, val bounds: Rectangle, val primary: Boolean)

internal fun hudDisplays(): List<HudDisplay> {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = environment.defaultScreenDevice.iDstring
    return environment.screenDevices.map { HudDisplay(it.iDstring, Rectangle(it.defaultConfiguration.bounds), it.iDstring == primary) }
}

internal fun selectHudDisplay(displays: List<HudDisplay>, id: String): HudDisplay? =
    displays.firstOrNull { it.id == id } ?: displays.firstOrNull { it.primary } ?: displays.firstOrNull()

/** Match the existing HUD bridge's conversion from AWT user coordinates to Compose dp. */
internal fun hudDisplayState(bounds: Rectangle, scaleX: Double, scaleY: Double, density: Float): Pair<WindowPosition.Absolute, DpSize> {
    require(bounds.width > 0 && bounds.height > 0 && scaleX.isFinite() && scaleX > 0 && scaleY.isFinite() && scaleY > 0 && density.isFinite() && density > 0)
    return WindowPosition.Absolute((bounds.x * scaleX / density).toFloat().dp, (bounds.y * scaleY / density).toFloat().dp) to
        DpSize((bounds.width * scaleX / density).toFloat().dp, (bounds.height * scaleY / density).toFloat().dp)
}

@Composable
internal fun PlaceHudOnDisplay(window: Window, state: WindowState, displayId: String) {
    val density by rememberUpdatedState(LocalDensity.current.density)
    DisposableEffect(window, state, displayId) {
        fun update() {
            val target = selectHudDisplay(hudDisplays(), displayId) ?: return
            // Do not use exclusive fullscreen; keep one ordinary transparent overlay window.
            if (window.bounds != target.bounds) window.bounds = target.bounds
            val transform = window.graphicsConfiguration.defaultTransform
            val (position, size) = hudDisplayState(target.bounds, transform.scaleX, transform.scaleY, density)
            if (state.position != position) state.position = position
            if (state.size != size) state.size = size
        }
        update()
        // AWT has no portable display-hotplug callback. Poll topology independently of telemetry.
        val timer = Timer(1000) { update() }.apply { start() }
        onDispose { timer.stop() }
    }
}
