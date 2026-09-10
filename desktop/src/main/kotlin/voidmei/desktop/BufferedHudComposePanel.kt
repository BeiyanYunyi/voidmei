package voidmei.desktop

import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.ceil

/** Present a complete Swing HUD frame in one blit, including its transparent pixels. */
internal class BufferedHudComposePanel(content: javax.swing.JComponent) : javax.swing.JPanel(java.awt.BorderLayout()) {
    init {
        // This component replaces every pixel, including alpha, in paint().
        // Stop Swing from repainting/clearing transparent ancestors first.
        isOpaque = true
        isDoubleBuffered = false
        add(content, java.awt.BorderLayout.CENTER)
    }
    private var frame: BufferedImage? = null

    // Child Skia repaint requests must pass through this panel's complete-frame buffer.
    override fun isPaintingOrigin(): Boolean = true

    // The offscreen frame is explicitly cleared below; never fill a Swing background.
    override fun paintComponent(graphics: Graphics) = Unit

    override fun paint(graphics: Graphics) {
        if (width <= 0 || height <= 0) return
        val target = graphics as Graphics2D
        val scaleX = target.transform.scaleX
        val scaleY = target.transform.scaleY
        if (scaleX <= 0 || scaleY <= 0) return
        val pixelsWide = ceil(width * scaleX).toInt().coerceAtLeast(1)
        val pixelsHigh = ceil(height * scaleY).toInt().coerceAtLeast(1)
        val buffer = frame?.takeIf { it.width == pixelsWide && it.height == pixelsHigh } ?: run {
            frame?.flush()
            BufferedImage(pixelsWide, pixelsHigh, BufferedImage.TYPE_INT_ARGB_PRE).also { frame = it }
        }
        val offscreen = buffer.createGraphics()
        try {
            offscreen.composite = AlphaComposite.Clear
            offscreen.fillRect(0, 0, pixelsWide, pixelsHigh)
            offscreen.composite = AlphaComposite.SrcOver
            offscreen.scale(scaleX, scaleY)
            super.paint(offscreen)
        } finally { offscreen.dispose() }
        val presentation = target.create() as Graphics2D
        try {
            // Replace alpha as well as RGB; SrcOver would leave old pixels behind.
            presentation.composite = AlphaComposite.Src
            presentation.drawImage(buffer, 0, 0, width, height, null)
        } finally { presentation.dispose() }
    }

    override fun removeNotify() {
        frame?.flush()
        frame = null
        super.removeNotify()
    }
}
