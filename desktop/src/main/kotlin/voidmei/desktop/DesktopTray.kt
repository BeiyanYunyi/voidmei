package voidmei.desktop

import java.awt.*
import java.awt.image.BufferedImage

/** Returns null when the desktop cannot provide a recoverable tray entry. */
internal fun installDesktopTray(onShow: () -> Unit, onHud: () -> Unit, onExit: () -> Unit, onAvailability: (Boolean) -> Unit = {}): AutoCloseable? {
    if (!SystemTray.isSupported()) return null
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().let { g ->
        try {
            g.color = Color(0x11, 0x18, 0x20); g.fillOval(0, 0, 32, 32)
            g.color = Color(0x84, 0xDE, 0xC6); g.stroke = BasicStroke(3f)
            g.drawLine(8, 8, 16, 24); g.drawLine(16, 24, 24, 8)
        } finally { g.dispose() }
    }
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
    }
    val tray = SystemTray.getSystemTray()
    tray.add(icon)
    val availability = TrayAvailabilityListener(onAvailability)
    tray.addPropertyChangeListener("systemTray", availability)
    return AutoCloseable {
        availability.close()
        tray.removePropertyChangeListener("systemTray", availability)
        tray.remove(icon)
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
internal fun restoreMainWindow(window: Frame) {
    check(EventQueue.isDispatchThread())
    if (!window.isDisplayable) return
    window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
    window.isVisible = true
    window.toFront()
    window.requestFocus()
}
