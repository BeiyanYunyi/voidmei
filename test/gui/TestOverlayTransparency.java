import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.alee.laf.rootpane.WebFrame;
import prog.Application;
import ui.WebLafSettings;
import ui.util.OverlayStyleHelper;

/**
 * Display-dependent regression test; run on X11/XWayland or Xvfb (24-bit screen).
 * Kept separate from the headless unit suite because it needs WebLaF and a display.
 * javac -cp 'bin:dep/*' -d bin test/gui/TestOverlayTransparency.java
 * java -cp 'bin:dep/*' TestOverlayTransparency
 */
public class TestOverlayTransparency {
    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                Application.defaultFont = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12);
                Application.initWebLaf();
                checkOverlay(false);
                checkOverlay(true);
            });
            System.out.println("Overlay transparency tests passed");
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        // WebLaF starts background services even after all frames close.
        System.exit(0);
    }

    private static void checkOverlay(boolean styleHelper) {
        WebFrame frame = new WebFrame();
        try {
            require(frame.getGraphicsConfiguration().getDevice()
                    .isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT),
                    "Test display must support per-pixel transparency");
            // Reproduce an opaque window/root/content even on platforms where
            // WebLaF's automatic transparency happens to work.
            frame.setBackground(Color.LIGHT_GRAY);
            frame.getRootPane().setOpaque(true);
            ((JComponent) frame.getContentPane()).setOpaque(true);
            frame.getContentPane().setBackground(Color.LIGHT_GRAY);
            if (styleHelper) {
                OverlayStyleHelper.applyTransparentStyle(frame);
            } else {
                frame.setShadeWidth(0);
                WebLafSettings.setWindowOpaque(frame);
            }
            frame.setSize(200, 150);
            frame.setVisible(true);
            checkTransparent(frame);
            require(centerAlpha(frame) == 0, "Game overlay must paint no background");
            frame.setSize(230, 170);
            frame.validate();
            checkTransparent(frame);
            require(centerAlpha(frame) == 0, "Resized overlay must remain transparent");
            OverlayStyleHelper.applyPreviewStyle(frame);
            require(centerAlpha(frame) == Application.previewColor.getAlpha(),
                    "Preview must retain its configured translucent background");
            OverlayStyleHelper.applyTransparentStyle(frame);
            require(centerAlpha(frame) == 0, "Leaving preview must clear its background");
        } finally {
            prog.AlwaysOnTopCoordinator.getInstance().unregisterOverlay(frame);
            frame.dispose();
        }
    }

    private static void checkTransparent(WebFrame frame) {
        require(!frame.isOpaque(), "Native window must support alpha");
        require(!frame.getRootPane().isOpaque(), "Root pane must be non-opaque");
        require(!((JComponent) frame.getContentPane()).isOpaque(), "Content must be non-opaque");
        require(frame.getContentPane().getBackground().getAlpha() == 0,
                "Content background must be transparent");
    }

    private static int centerAlpha(WebFrame frame) {
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            frame.getRootPane().paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
