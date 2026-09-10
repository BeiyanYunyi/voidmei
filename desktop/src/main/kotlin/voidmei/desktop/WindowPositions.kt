package voidmei.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition as ComposePosition
import voidmei.config.WindowPosition
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Insets
import java.awt.Toolkit
import java.awt.GraphicsConfiguration

internal fun windowWorkArea(bounds: Rectangle, insets: Insets): Rectangle? {
    if (listOf(insets.top, insets.bottom, insets.left, insets.right).any { it < 0 }) return null
    val width = bounds.width.toLong() - insets.left - insets.right
    val height = bounds.height.toLong() - insets.top - insets.bottom
    val x = bounds.x.toLong() + insets.left
    val y = bounds.y.toLong() + insets.top
    if (width !in 1..Int.MAX_VALUE.toLong() || height !in 1..Int.MAX_VALUE.toLong() ||
        x !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || y !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
    return Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
}

private fun workArea(config: GraphicsConfiguration): Rectangle? =
    windowWorkArea(config.bounds, Toolkit.getDefaultToolkit().getScreenInsets(config))

/** Keep a draggable title area on a connected monitor, including negative monitor coordinates. */
internal fun visiblePosition(position: WindowPosition?, screens: List<Rectangle>): WindowPosition? = position?.takeIf { point ->
    screens.any { screen ->
        screen.width > 0 && screen.height > 0 &&
            point.x.toDouble() >= screen.x && point.x.toDouble() + 100 <= screen.x.toLong() + screen.width &&
            point.y.toDouble() >= screen.y && point.y.toDouble() + 32 <= screen.y.toLong() + screen.height
    }
}

internal fun restorePosition(position: WindowPosition?): ComposePosition {
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.mapNotNull { workArea(it.defaultConfiguration) }
    return visiblePosition(position, screens)?.let { ComposePosition(it.x.dp, it.y.dp) } ?: ComposePosition.PlatformDefault
}

internal fun resetWindowPosition(offset: Int = 32): ComposePosition {
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val bounds = workArea(config) ?: config.bounds
    return ComposePosition((bounds.x.toFloat() + offset.coerceIn(0, (bounds.width - 100).coerceAtLeast(0))).dp,
        (bounds.y.toFloat() + offset.coerceIn(0, (bounds.height - 32).coerceAtLeast(0))).dp)
}
