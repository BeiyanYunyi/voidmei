import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only agent: request normal closure of this process's main window. */
public final class CloseWindowAgent {
    private static Frame firstHud;
    private static java.awt.Rectangle hudBounds;
    private static int hudSamples;
    private static boolean hudChanged;

    public static void premain(String requestPath, Instrumentation instrumentation) {
        if (Files.exists(Path.of(requestPath).getParent().resolve("require-single-hud"))) {
            EventQueue.invokeLater(() -> new javax.swing.Timer(250, event -> {
                Frame current = null;
                int count = 0;
                for (Frame frame : Frame.getFrames()) {
                    if (frame.isDisplayable() && "VoidMei HUD".equals(frame.getTitle())) { current = frame; count++; }
                }
                if (count == 1 && current.isVisible()) {
                    // The scene applies its configured size after the native window is created.
                    if (firstHud == null) {
                        if (current.getWidth() != 900 || current.getHeight() != 600) return;
                        firstHud = current; hudBounds = current.getBounds();
                    }
                    else if (current != firstHud || !current.getBounds().equals(hudBounds)) hudChanged = true;
                    hudSamples++;
                } else if (firstHud != null || count > 1) hudChanged = true;
            }).start());
        }
        Thread watcher = new Thread(() -> {
            try {
                while (!Files.exists(Path.of(requestPath))) Thread.sleep(50);
                EventQueue.invokeLater(() -> {
                    for (Frame frame : Frame.getFrames()) {
                        if (frame.isDisplayable() && "VoidMei · Kotlin".equals(frame.getTitle())) {
                            if (Files.exists(Path.of(requestPath).getParent().resolve("require-single-hud"))) {
                                if (hudChanged || hudSamples < 5) throw new AssertionError("HUD window changed or was not observed long enough");
                                System.out.println("[VoidMei exit test] single HUD stable samples=" + hudSamples + " bounds=" + hudBounds);
                                // Xvfb has no window manager to implement the always-on-top hint.
                                firstHud.toFront();
                            }
                            if (Files.exists(Path.of(requestPath).getParent().resolve("require-visible"))) {
                                if (!frame.isVisible()) throw new AssertionError("Recovery main window is hidden");
                                System.out.println("[VoidMei exit test] recovery main window visible");
                            }
                            if (Files.exists(Path.of(requestPath).getParent().resolve("require-tray-background"))) {
                                if (frame.isVisible()) throw new AssertionError("Background main window is visible");
                                if (!java.awt.SystemTray.isSupported() || java.awt.SystemTray.getSystemTray().getTrayIcons().length != 1)
                                    throw new AssertionError("Background app has no unique tray entry");
                                System.out.println("[VoidMei exit test] background main hidden with tray entry");
                            }
                            frame.setLocation(frame.getX() + 37, frame.getY() + 29);
                            javax.swing.Timer close = new javax.swing.Timer(50, event -> {
                                try {
                                    Path root = Path.of(requestPath).getParent();
                                    if (firstHud != null) {
                                        try {
                                            var image = new java.awt.Robot().createScreenCapture(hudBounds);
                                            javax.imageio.ImageIO.write(image, "png", root.resolve("hud-scene.png").toFile());
                                            int yellow = 0;
                                            int sky = 0, ground = 0, compass = 0, elevator = 0;
                                            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                                                int rgb = image.getRGB(x, y);
                                                if (((rgb >> 16) & 255) > 200 && ((rgb >> 8) & 255) > 180 && (rgb & 255) < 80) yellow++;
                                                int color = rgb & 0xffffff;
                                                if (x >= 300 && x < 580 && y >= 380 && y < 600) {
                                                    if (color == 0x1e526f) sky++;
                                                    if (color == 0x644e3c) ground++;
                                                }
                                                if (x >= 825 && x < 860 && y >= 300 && y < 500 && color == 0x84dec6) elevator++;
                                                if (x >= 300 && x < 460 && y >= 180 && y < 360 && color == 0xffd580) compass++;
                                            }
                                            if (elevator < 20) throw new AssertionError("HUD elevator position marker not visible at +67%: " + elevator);
                                            if (yellow < 30) throw new AssertionError("HUD map/crosshair pixels not visible");
                                            if (sky < 500 || ground < 500 || compass < 20)
                                                throw new AssertionError("HUD instruments not visible: sky=" + sky + " ground=" + ground + " compass=" + compass);
                                            System.out.println("[VoidMei exit test] HUD elevator pixels=" + elevator);
                                            System.out.println("[VoidMei exit test] HUD yellow pixels=" + yellow);
                                            System.out.println("[VoidMei exit test] HUD instruments sky=" + sky + " ground=" + ground + " compass=" + compass);
                                        } catch (java.awt.AWTException error) { throw new RuntimeException(error); }
                                    }
                                    Files.copy(root.resolve("config/settings-kmp.json"), root.resolve("pre-close-settings.json"));
                                    var transform = frame.getGraphicsConfiguration().getDefaultTransform();
                                    System.out.println("[VoidMei exit test] position=" + frame.getX() + "," + frame.getY()
                                        + " scale=" + transform.getScaleX() + "," + transform.getScaleY());
                                    System.out.println("[VoidMei exit test] dispatch WINDOW_CLOSING");
                                    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                                } catch (java.io.IOException error) {
                                    throw new java.io.UncheckedIOException(error);
                                }
                            });
                            close.setRepeats(false);
                            close.start();
                            return;
                        }
                    }
                    System.err.println("[VoidMei exit test] main window not found");
                });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "voidmei-test-close-request");
        watcher.setDaemon(true);
        watcher.start();
    }
}
