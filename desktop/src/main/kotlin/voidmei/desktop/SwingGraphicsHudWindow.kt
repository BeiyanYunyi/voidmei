package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Color
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import kotlin.math.roundToInt

/** Offscreen Skia rendering with Swing presentation, avoiding native transparent swap buffers. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SwingGraphicsHudWindow(
    state: WindowState,
    onCloseRequest: () -> Unit,
    visible: Boolean = true,
    content: @Composable WindowScope.() -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val close = rememberUpdatedState(onCloseRequest)
    val currentVisible = rememberUpdatedState(visible)
    var nativeFrame by remember(state) { mutableStateOf<JFrame?>(null) }
    SideEffect {
        nativeFrame?.let {
            if (it.isVisible != visible) {
                it.isVisible = visible
                if (visible) clearHudSwingBackgrounds(it)
            }
        }
    }
    DisposableEffect(state) {
        val frame = JFrame("VoidMei HUD")
        val panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics())
        val scope = object : WindowScope { override val window = frame }
        frame.isUndecorated = true
        frame.background = Color(0, 0, 0, 0)
        frame.isAlwaysOnTop = true
        frame.focusableWindowState = false
        frame.isResizable = false
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) = close.value()
        })
        panel.isOpaque = false
        panel.background = Color(0, 0, 0, 0)
        frame.rootPane.isOpaque = false
        frame.layeredPane.isOpaque = false
        (frame.contentPane as javax.swing.JComponent).isOpaque = false
        var density = 1f
        var applyingPosition = false
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) {
                if (!applyingPosition) {
                    val transform = frame.graphicsConfiguration.defaultTransform
                    state.position = WindowPosition.Absolute(
                        (frame.x * transform.scaleX / density).toFloat().dp,
                        (frame.y * transform.scaleY / density).toFloat().dp)
                }
            }
        }
        frame.addComponentListener(listener)
        panel.setContent {
            density = LocalDensity.current.density
            val size = state.size
            val position = state.position
            SideEffect {
                val transform = frame.graphicsConfiguration.defaultTransform
                val width = (size.width.value * density / transform.scaleX).roundToInt().coerceAtLeast(1)
                val height = (size.height.value * density / transform.scaleY).roundToInt().coerceAtLeast(1)
                if (frame.width != width || frame.height != height) frame.setSize(width, height)
                if (position is WindowPosition.Absolute) {
                    val x = (position.x.value * density / transform.scaleX).roundToInt()
                    val y = (position.y.value * density / transform.scaleY).roundToInt()
                    if (frame.x != x || frame.y != y) {
                        applyingPosition = true
                        try { frame.setLocation(x, y) } finally { applyingPosition = false }
                    }
                }
            }
            currentContent.value(scope)
        }
        frame.contentPane.add(BufferedHudComposePanel(panel))
        frame.setSize(state.size.width.value.roundToInt().coerceAtLeast(1),
            state.size.height.value.roundToInt().coerceAtLeast(1))
        (state.position as? WindowPosition.Absolute)?.let {
            frame.setLocation(it.x.value.roundToInt(), it.y.value.roundToInt())
        }
        frame.isVisible = currentVisible.value
        nativeFrame = frame
        clearHudSwingBackgrounds(frame)
        println("[VoidMei HUD] Presentation: SwingGraphics; full HUD")
        onDispose {
            nativeFrame = null
            frame.removeComponentListener(listener)
            panel.dispose()
            frame.dispose()
        }
    }
}

/** Clear every Swing container background, including children created during addNotify. */
internal fun clearHudSwingBackgrounds(component: java.awt.Component) {
    component.background = Color(0, 0, 0, 0)
    (component as? javax.swing.JComponent)?.let {
        it.isOpaque = it is BufferedHudComposePanel
        // The HUD owns its complete-frame buffer. Do not route it through the
        // RepaintManager buffer shared with other Swing windows.
        it.isDoubleBuffered = false
    }
    (component as? java.awt.Container)?.components?.forEach(::clearHudSwingBackgrounds)
    component.repaint()
}
