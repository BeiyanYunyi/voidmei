package ui.util;

import java.awt.Color;
import java.awt.GraphicsDevice.WindowTranslucency;
import javax.swing.JComponent;
import com.alee.laf.rootpane.WebFrame;
import prog.Application;
import prog.config.OverlaySettings;

/**
 * Utility class for common overlay window styling operations.
 * Extracted from repeated patterns across multiple overlay classes.
 *
 * <p>This helper consolidates the ~400 lines of duplicate styling code
 * found in AttitudeOverlay, ControlSurfacesOverlay, MiniHUDOverlay,
 * GearFlapsOverlay, DrawFrame, and DrawFrameSimpl.
 */
public final class OverlayStyleHelper {

    private OverlayStyleHelper() {
        // Prevent instantiation
    }

    /**
     * Apply transparent window style (game mode).
     * Replaces the setFrameOpaque() pattern found in 6+ overlay files.
     *
     * @param frame The WebFrame to style
     */
    public static void applyTransparentStyle(WebFrame frame) {
        enableWindowTransparency(frame);
        frame.getWebRootPaneUI().setMiddleBg(new Color(0, 0, 0, 0));
        frame.getWebRootPaneUI().setTopBg(new Color(0, 0, 0, 0));
        frame.getWebRootPaneUI().setBorderColor(new Color(0, 0, 0, 0));
        frame.getWebRootPaneUI().setInnerBorderColor(new Color(0, 0, 0, 0));
        frame.setShadeWidth(0);
    }

    /**
     * Enable native per-pixel alpha independently of WebLaF 1.29's legacy
     * transparency probe. On X11 the default graphics configuration may be
     * opaque even when the device has an ARGB configuration; Window.setBackground
     * selects that configuration for us.
     * Call before showing the overlay. Preview colors are painted by WebLaF.
     */
    public static void enableWindowTransparency(WebFrame frame) {
        if (!frame.getGraphicsConfiguration().getDevice()
                .isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            prog.util.Logger.warn("Per-pixel transparency unavailable for overlay: " + frame.getTitle());
            return;
        }
        if (!frame.isUndecorated()) {
            frame.setUndecorated(true);
        }
        Color transparent = new Color(0, 0, 0, 0);
        frame.setBackground(transparent);
        frame.getRootPane().setBackground(transparent);
        frame.getRootPane().setOpaque(false);
        frame.getContentPane().setBackground(transparent);
        if (frame.getContentPane() instanceof JComponent) {
            ((JComponent) frame.getContentPane()).setOpaque(false);
        }
    }

    /**
     * Apply preview mode styling.
     * Used when overlay is shown in settings UI for WYSIWYG editing.
     *
     * @param frame The WebFrame to style
     */
    public static void applyPreviewStyle(WebFrame frame) {
        frame.getWebRootPaneUI().setMiddleBg(Application.previewColor);
        frame.getWebRootPaneUI().setTopBg(Application.previewColor);
        frame.setCursor(null);
    }

    /**
     * Load font configuration with defaults fallback.
     *
     * @param settings The overlay settings (may be null)
     * @return Immutable font configuration
     */
    public static FontConfig loadFontConfig(OverlaySettings settings) {
        String fontName;
        String numFont;
        int fontAdd;

        if (settings != null) {
            fontName = settings.getFontName();
            numFont = settings.getNumFontName();
            fontAdd = settings.getFontSizeAdd();
        } else {
            fontName = Application.defaultFontName;
            numFont = Application.defaultNumfontName;
            fontAdd = 0;
        }

        return new FontConfig(fontName, numFont, fontAdd);
    }

    /**
     * Immutable font configuration holder.
     */
    public static final class FontConfig {
        public final String fontName;
        public final String numFontName;
        public final int fontSizeAdd;

        public FontConfig(String fontName, String numFontName, int fontSizeAdd) {
            this.fontName = fontName;
            this.numFontName = numFontName;
            this.fontSizeAdd = fontSizeAdd;
        }
    }
}
