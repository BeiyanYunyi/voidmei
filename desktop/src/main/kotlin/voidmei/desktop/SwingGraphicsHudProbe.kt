package voidmei.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import java.awt.Color
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.Timer

/** Opt-in native presentation experiment; deliberately does not stand in for a functional HUD. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SwingGraphicsHudProbe(onCloseRequest: () -> Unit) {
    val close = rememberUpdatedState(onCloseRequest)
    DisposableEffect(Unit) {
        val panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics())
        val frame = JFrame("VoidMei HUD")
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
        panel.setContent {
            androidx.compose.material3.MaterialTheme {
                androidx.compose.material3.Text("VoidMei SwingGraphics presentation probe")
            }
        }
        frame.contentPane.add(panel)
        frame.setSize(440, 510)
        frame.setLocation(40, 40)
        frame.isVisible = true
        clearHudSwingBackgrounds(frame)
        var previous = ""
        val timer = Timer(1000) {
            val report = "绘制后端：${panel.renderApi}"
            if (report != previous) {
                previous = report
                println("[VoidMei HUD] $report\nPresentation probe: SwingGraphics; full HUD bypassed")
            }
        }.apply { start() }
        onDispose {
            timer.stop()
            panel.dispose()
            frame.dispose()
        }
    }
}
