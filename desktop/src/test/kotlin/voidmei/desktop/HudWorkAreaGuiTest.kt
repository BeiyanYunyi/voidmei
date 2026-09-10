package voidmei.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.test.*

class HudWorkAreaGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingAnActualAwtWindowUpdatesAvailableHeight() {
        lateinit var window: JWindow
        SwingUtilities.invokeAndWait {
            window = JWindow()
            val bounds = window.graphicsConfiguration.bounds
            window.setBounds(bounds.x + 20, bounds.y + 40, 100, 100)
            window.isVisible = true
        }
        var available: Double? = null
        try {
            compose.setContent {
                val current = rememberHudAvailablePixels(window)
                SideEffect { available = current?.height }
            }
            compose.waitUntil(5000) { available != null }
            val initial = available!!
            var expected = 0.0
            SwingUtilities.invokeAndWait {
                val config = window.graphicsConfiguration
                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
                val y = maxOf(window.y, config.bounds.y + insets.top) + 50
                expected = hudAvailableHeightPixels(config.bounds.y, config.bounds.height, insets.top,
                    insets.bottom, y, config.defaultTransform.scaleY)!!
                window.setLocation(window.x, y)
            }
            compose.waitUntil(5000) { available == expected }
            assertTrue(available!! < initial)
            lateinit var repaired: java.awt.Point
            SwingUtilities.invokeAndWait {
                val config = window.graphicsConfiguration
                val bounds = config.bounds
                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
                // Use the screen edge rather than assuming the desktop has a taskbar.
                val x = bounds.x + bounds.width - 1
                val y = bounds.y + bounds.height - 1
                // Compose test density and this display are both reported by their providers.
                val minimum = kotlin.math.ceil(120.0 * compose.density.density / config.defaultTransform.scaleY).toInt()
                repaired = hudSafeOrigin(bounds, insets, x, y, window.width, minimum)!!
                expected = hudAvailableHeightPixels(bounds.y, bounds.height, insets.top, insets.bottom,
                    repaired.y, config.defaultTransform.scaleY)!!
                window.setLocation(x, y)
            }
            compose.waitUntil(5000) { available == expected }
            SwingUtilities.invokeAndWait { assertEquals(repaired, window.location) }
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }
}
