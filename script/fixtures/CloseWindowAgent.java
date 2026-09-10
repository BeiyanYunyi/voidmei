import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only agent: request normal closure of this process's main window. */
public final class CloseWindowAgent {
    public static void premain(String requestPath, Instrumentation instrumentation) {
        Thread watcher = new Thread(() -> {
            try {
                while (!Files.exists(Path.of(requestPath))) Thread.sleep(50);
                EventQueue.invokeLater(() -> {
                    for (Frame frame : Frame.getFrames()) {
                        if (frame.isDisplayable() && "VoidMei · Kotlin".equals(frame.getTitle())) {
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
