package voidmei.desktop

import androidx.compose.runtime.*
import java.awt.Window
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.awt.Point
import java.awt.Rectangle
import java.awt.Insets
import androidx.compose.ui.platform.LocalDensity
import javax.swing.Timer
import kotlin.math.ceil
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

@Composable
internal fun updateHudWindowSize(window: Window, state: WindowState, preferredWidth: Int, contentHeight: Dp) {
    val available = rememberHudAvailablePixels(window)
    val density = LocalDensity.current.density
    LaunchedEffect(window, state, contentHeight, available, density, preferredWidth) {
        val target = DpSize(hudWidthDp(preferredWidth, available?.width, density).dp,
            hudHeightDp(contentHeight.value, available?.height, density).dp)
        // A transient warning or a digit wrapping must not repeatedly resize the transparent
        // native surface. Grow immediately; shrink only after the measured layout settles.
        // Display/work-area constraints and explicit width changes still take effect immediately.
        val maximumHeight = hudHeightDp(900f, available?.height, density).dp
        if (target.width == state.size.width && target.height < state.size.height && state.size.height <= maximumHeight)
            kotlinx.coroutines.delay(1000)
        if (state.size != target) state.size = target
    }
}

/** Keep the whole header width and enough height to operate it inside the work area. */
internal fun hudSafeOrigin(bounds: Rectangle, insets: Insets, x: Int, y: Int, width: Int,
    minimumHeight: Int): Point? {
    if (bounds.width <= 0 || bounds.height <= 0 || listOf(insets.top, insets.bottom, insets.left, insets.right).any { it < 0 }) return null
    val left = bounds.x.toLong() + insets.left
    val right = bounds.x.toLong() + bounds.width - insets.right
    val top = bounds.y.toLong() + insets.top
    val bottom = bounds.y.toLong() + bounds.height - insets.bottom
    if (right <= left || bottom <= top) return null
    val lastX = right - width.toLong().coerceIn(1, right - left)
    val lastY = bottom - minimumHeight.toLong().coerceIn(1, bottom - top)
    fun Long.coordinate() = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    return Point(x.toLong().coerceIn(left, lastX).coordinate(), y.toLong().coerceIn(top, lastY).coordinate())
}

/** AWT coordinates are user-space units; convert them to device pixels before Compose dp. */
internal fun hudAvailableHeightPixels(screenTop: Int, screenHeight: Int, topInset: Int, bottomInset: Int,
    windowTop: Int, scaleY: Double): Double? {
    if (screenHeight <= 0 || topInset < 0 || bottomInset < 0 || !scaleY.isFinite() || scaleY <= 0) return null
    val top = screenTop.toLong() + topInset
    val bottom = screenTop.toLong() + screenHeight - bottomInset
    if (bottom <= top) return null
    val height = bottom - windowTop.toLong().coerceIn(top, bottom - 1)
    return (height * scaleY).takeIf { it.isFinite() && it > 0 }
}

internal fun hudHeightDp(content: Float, availablePixels: Double?, density: Float): Float {
    val available = availablePixels?.takeIf { it.isFinite() && it > 0 }
        ?.div(density.takeIf { it.isFinite() && it > 0 } ?: 1f)?.toFloat() ?: 900f
    val maximum = minOf(900f, available).coerceAtLeast(1f)
    return (content.takeIf { it.isFinite() && it > 0 } ?: 120f).coerceIn(minOf(120f, maximum), maximum)
}

internal fun hudAvailableWidthPixels(screenWidth: Int, leftInset: Int, rightInset: Int, scaleX: Double): Double? {
    if (screenWidth <= 0 || leftInset < 0 || rightInset < 0 || !scaleX.isFinite() || scaleX <= 0) return null
    val width = screenWidth.toLong() - leftInset - rightInset
    return (width * scaleX).takeIf { it.isFinite() && it > 0 }
}

internal fun hudWidthDp(preferred: Int, availablePixels: Double?, density: Float): Float {
    val maximum = availablePixels?.takeIf { it.isFinite() && it > 0 }
        ?.div(density.takeIf { it.isFinite() && it > 0 } ?: 1f) ?: 1000.0
    return minOf(preferred.coerceIn(240, 1000).toDouble(), maximum).coerceAtLeast(1.0).toFloat()
}

internal data class HudAvailableSpace(val width: Double?, val height: Double?)

@Composable
internal fun rememberHudAvailablePixels(window: Window): HudAvailableSpace? {
    var available by remember(window) { mutableStateOf<HudAvailableSpace?>(null) }
    val density = LocalDensity.current.density
    DisposableEffect(window, density) {
        // Repair only after movement settles, so crossing monitor boundaries is not obstructed.
        val repair = Timer(200) {
            try {
                val config = window.graphicsConfiguration
                val scale = config.defaultTransform.scaleY
                if (scale.isFinite() && scale > 0) {
                    val minimum = ceil(120.0 * density / scale).toInt().coerceAtLeast(1)
                    val origin = hudSafeOrigin(config.bounds, Toolkit.getDefaultToolkit().getScreenInsets(config),
                        window.x, window.y, window.width, minimum)
                    if (origin != null && origin != window.location) window.location = origin
                }
            } catch (_: java.awt.HeadlessException) { }
              catch (_: SecurityException) { }
        }.apply { isRepeats = false }
        fun refresh() {
            available = try {
                val config = window.graphicsConfiguration
                val bounds = config.bounds
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
                HudAvailableSpace(
                    hudAvailableWidthPixels(bounds.width, insets.left, insets.right, config.defaultTransform.scaleX),
                    hudAvailableHeightPixels(bounds.y, bounds.height, insets.top, insets.bottom, window.y,
                        config.defaultTransform.scaleY))
            } catch (_: java.awt.HeadlessException) { null }
              catch (_: SecurityException) { null }
            repair.restart()
        }
        val listener = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = refresh()
            override fun componentResized(e: ComponentEvent) = refresh()
            override fun componentShown(e: ComponentEvent) = refresh()
        }
        val configuration = PropertyChangeListener { refresh() }
        window.addComponentListener(listener)
        window.addPropertyChangeListener("graphicsConfiguration", configuration)
        refresh()
        onDispose {
            repair.stop()
            window.removeComponentListener(listener)
            window.removePropertyChangeListener("graphicsConfiguration", configuration)
        }
    }
    return available
}
