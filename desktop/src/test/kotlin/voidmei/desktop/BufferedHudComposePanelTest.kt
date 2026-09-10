package voidmei.desktop

import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.*

class BufferedHudComposePanelTest {
    @Test fun scaledFramesReplaceAlphaWithoutClippingOrAccumulatingOldPixels() {
        SwingUtilities.invokeAndWait {
            var visible = true
            val child = object : JComponent() {
                override fun paintComponent(graphics: Graphics) {
                    if (visible) {
                        graphics.color = Color(255, 0, 0, 128)
                        graphics.fillRect(0, 0, width, height)
                        graphics.color = Color.BLUE
                        graphics.fillRect(width - 4, height - 4, 4, 4)
                    }
                }
            }
            val panel = BufferedHudComposePanel(child)
            for (scale in listOf(1.0, 1.25, 1.5, 2.0)) {
                // Odd dimensions exercise ceil-to-device-pixel allocation at fractional DPI.
                panel.setSize(37, 19)
                panel.doLayout()
                val output = BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB)
                fun draw() {
                    val graphics = output.createGraphics()
                    try {
                        graphics.translate(3, 5)
                        graphics.scale(scale, scale)
                        panel.paint(graphics)
                    } finally { graphics.dispose() }
                }
                fun pixel(x: Double, y: Double) = Color(output.getRGB((3 + x * scale).toInt(), (5 + y * scale).toInt()), true)
                visible = true
                draw()
                repeat(3) { draw() }
                assertEquals(Color(255, 0, 0, 128), pixel(10.0, 8.0), "Alpha accumulated at scale $scale")
                assertEquals(Color.BLUE, pixel(35.0, 17.0), "Bottom/right edge clipped at scale $scale")
                assertEquals(0, Color(output.getRGB(0, 0), true).alpha)
                visible = false
                draw()
                assertEquals(0, pixel(10.0, 8.0).alpha, "Old translucent pixels remained at scale $scale")
                assertEquals(0, pixel(35.0, 17.0).alpha, "Old opaque pixels remained at scale $scale")
                visible = true
                panel.setSize(19, 13)
                panel.doLayout()
                draw()
                assertEquals(Color.BLUE, pixel(17.0, 11.0), "Resized frame used stale bounds at scale $scale")
                output.flush()
            }
        }
    }
}
