package voidmei.desktop

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

internal interface DesktopTray : AutoCloseable {
    fun showMessage(title: String, message: String)
}

/** Returns null when the desktop cannot provide a recoverable tray entry. */
internal fun installDesktopTray(onShow: () -> Unit, onHud: () -> Unit, onExit: () -> Unit, onAvailability: (Boolean) -> Unit = {}): DesktopTray? {
    val image = desktopTrayImage()
    StatusNotifierTray.install(image, onShow, onHud, onExit, onAvailability)?.let { return it }
    if (!SystemTray.isSupported()) return null
    return installAwtDesktopTray(image, onShow, onHud, onExit, onAvailability)
}

internal fun desktopTrayImage(): BufferedImage {
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().let { g ->
        try {
            g.color = Color(0x11, 0x18, 0x20); g.fillOval(0, 0, 32, 32)
            g.color = Color(0x84, 0xDE, 0xC6); g.stroke = BasicStroke(3f)
            g.drawLine(8, 8, 16, 24); g.drawLine(16, 24, 24, 8)
        } finally { g.dispose() }
    }
    return image
}

internal fun installAwtDesktopTray(image: BufferedImage, onShow: () -> Unit, onHud: () -> Unit, onExit: () -> Unit,
    onAvailability: (Boolean) -> Unit = {}): DesktopTray {
    val menu = PopupMenu()
    fun item(label: String, action: () -> Unit) = MenuItem(label).also {
        it.addActionListener { action() }; menu.add(it)
    }
    item("显示主窗口", onShow)
    item("切换 HUD", onHud)
    menu.addSeparator()
    item("退出 VoidMei", onExit)
    val icon = TrayIcon(image, "VoidMei", menu).apply {
        isImageAutoSize = true
        addActionListener { onShow() }
        addMouseListener(TrayShowMouseListener(onShow))
    }
    val tray = SystemTray.getSystemTray()
    val previousFrames = Frame.getFrames().toSet()
    tray.add(icon)
    val eventBridge = try { X11TrayEventBridge.install(previousFrames) }
        catch (error: Throwable) { tray.remove(icon); throw error }
    val availability = TrayAvailabilityListener(onAvailability)
    tray.addPropertyChangeListener("systemTray", availability)
    val messages = TrayMessages({ title, message -> icon.displayMessage(title, message, TrayIcon.MessageType.INFO) },
        { icon in tray.trayIcons })
    return object : DesktopTray {
        override fun showMessage(title: String, message: String) = messages.show(title, message)
        override fun close() {
            messages.close()
            availability.close()
            tray.removePropertyChangeListener("systemTray", availability)
            eventBridge?.close()
            tray.remove(icon)
        }
    }
}

/** AWT action events alone do not handle a normal single click on the tray icon. */
internal class TrayShowMouseListener(private val onShow: () -> Unit) : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
        if (event.button == MouseEvent.BUTTON1 && !event.isPopupTrigger) onShow()
    }
}

internal fun shouldStartInTray(preference: Boolean, trayAvailable: Boolean, recovery: Boolean, settingsError: Boolean) =
    preference && trayAvailable && !recovery && !settingsError

/** Native tray events may be queued; disposal must also invalidate pending callbacks. */
internal class TrayAvailabilityListener(private val update: (Boolean) -> Unit) : java.beans.PropertyChangeListener, AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun propertyChange(event: java.beans.PropertyChangeEvent) {
        if (event.propertyName != "systemTray" || closed.get()) return
        val available = event.newValue != null
        EventQueue.invokeLater { if (!closed.get()) update(available) }
    }
    override fun close() { closed.set(true) }
}

/** Called only for an explicit restore request, never for routine telemetry updates. */
internal fun restoreDesktopWindow(window: Frame) {
    check(EventQueue.isDispatchThread())
    if (!window.isDisplayable) return
    window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
    window.isVisible = true
    window.toFront()
    window.requestFocus()
}

/** Marshals delivery to AWT and invalidates messages pending when the tray is disposed. */
internal class TrayMessages(private val deliver: (String, String) -> Unit, private val available: () -> Boolean = { true }) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    fun show(title: String, message: String) {
        EventQueue.invokeLater {
            if (!closed.get()) try {
                if (available()) deliver(title, message)
            } catch (e: Exception) { println("[VoidMei tray] 通知不可用：${e.message}") }
        }
    }
    override fun close() { closed.set(true) }
}
