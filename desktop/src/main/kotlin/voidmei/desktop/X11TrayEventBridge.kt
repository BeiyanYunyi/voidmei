package voidmei.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.Frame
import javax.swing.Timer

/**
 * KDE's XEmbed/SNI proxy sends synthetic button events to the embedded frame, while
 * OpenJDK selects button events only on its content/Canvas children. Forward those
 * otherwise lost events to the Canvas so AWT still handles clicks and popup menus.
 * Uses public AWT/JNA component handles; no access to private JDK peers is required.
 */
internal object X11TrayEventBridge {
    internal interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XCloseDisplay(display: Pointer): Int
        fun XGetWindowAttributes(display: Pointer, window: NativeLong, attributes: Attributes): Int
        fun XSelectInput(display: Pointer, window: NativeLong, mask: NativeLong): Int
        fun XPending(display: Pointer): Int
        fun XNextEvent(display: Pointer, event: Pointer): Int
        fun XSendEvent(display: Pointer, window: NativeLong, propagate: Int, mask: NativeLong, event: Pointer): Int
        fun XFlush(display: Pointer): Int
    }

    @Structure.FieldOrder("x", "y", "width", "height", "borderWidth", "depth", "visual", "root", "windowClass",
        "bitGravity", "winGravity", "backingStore", "backingPlanes", "backingPixel", "saveUnder", "colormap",
        "mapInstalled", "mapState", "allEventMasks", "yourEventMask", "doNotPropagateMask", "overrideRedirect", "screen")
    internal class Attributes : Structure() {
        @JvmField var x = 0
        @JvmField var y = 0
        @JvmField var width = 0
        @JvmField var height = 0
        @JvmField var borderWidth = 0
        @JvmField var depth = 0
        @JvmField var visual: Pointer? = null
        @JvmField var root = NativeLong()
        @JvmField var windowClass = 0
        @JvmField var bitGravity = 0
        @JvmField var winGravity = 0
        @JvmField var backingStore = 0
        @JvmField var backingPlanes = NativeLong()
        @JvmField var backingPixel = NativeLong()
        @JvmField var saveUnder = 0
        @JvmField var colormap = NativeLong()
        @JvmField var mapInstalled = 0
        @JvmField var mapState = 0
        @JvmField var allEventMasks = NativeLong()
        @JvmField var yourEventMask = NativeLong()
        @JvmField var doNotPropagateMask = NativeLong()
        @JvmField var overrideRedirect = 0
        @JvmField var screen: Pointer? = null
    }

    @Structure.FieldOrder("type", "serial", "sendEvent", "display", "window", "root", "subwindow", "time",
        "x", "y", "xRoot", "yRoot", "state", "button", "sameScreen")
    internal class ButtonEvent(pointer: Pointer) : Structure(pointer) {
        @JvmField var type = 0
        @JvmField var serial = NativeLong()
        @JvmField var sendEvent = 0
        @JvmField var display: Pointer? = null
        @JvmField var window = NativeLong()
        @JvmField var root = NativeLong()
        @JvmField var subwindow = NativeLong()
        @JvmField var time = NativeLong()
        @JvmField var x = 0
        @JvmField var y = 0
        @JvmField var xRoot = 0
        @JvmField var yRoot = 0
        @JvmField var state = 0
        @JvmField var button = 0
        @JvmField var sameScreen = 0
    }

    private val xlib by lazy { Native.load("X11", Xlib::class.java) }

    fun install(previousFrames: Set<Frame>): AutoCloseable? {
        check(EventQueue.isDispatchThread())
        if (!Platform.isLinux()) return null
        val frame = Frame.getFrames().singleOrNull {
            it !in previousFrames && it.javaClass.name == "sun.awt.X11.XTrayIconPeer\$XTrayIconEmbeddedFrame"
        } ?: return null
        val canvas = frame.components.filterIsInstance<Canvas>().singleOrNull() ?: return null
        return forward(frame, canvas)
    }

    internal fun forward(frame: Frame, canvas: Canvas): AutoCloseable? {
        check(EventQueue.isDispatchThread())
        val display = checkNotNull(xlib.XOpenDisplay(null)) { "Cannot open the X11 tray display" }
        try {
            val source = NativeLong(Native.getWindowID(frame))
            val target = NativeLong(Native.getComponentID(canvas))
            val attributes = Attributes()
            check(xlib.XGetWindowAttributes(display, source, attributes) != 0)
            // A different/future JDK may already accept events on the outer window.
            if (attributes.allEventMasks.toLong() and 12L != 0L) {
                xlib.XCloseDisplay(display)
                return null
            }
            xlib.XSelectInput(display, source, NativeLong(12)) // ButtonPressMask | ButtonReleaseMask
            xlib.XFlush(display)
            val buffer = Memory(24L * Native.LONG_SIZE) // sizeof(XEvent)
            val event = ButtonEvent(buffer)
            var closed = false
            lateinit var timer: Timer
            val registration = AutoCloseable {
                check(EventQueue.isDispatchThread())
                if (!closed) {
                    closed = true
                    timer.stop()
                    xlib.XCloseDisplay(display)
                    buffer.close()
                }
            }
            timer = Timer(25) {
                if (!frame.isDisplayable || !canvas.isDisplayable) registration.close()
                else while (xlib.XPending(display) > 0) {
                    xlib.XNextEvent(display, buffer)
                    event.read()
                    if (event.sendEvent != 0 && event.window == source && event.type in 4..5) {
                        event.window = target
                        event.subwindow = NativeLong(0)
                        event.write()
                        xlib.XSendEvent(display, target, 0, NativeLong(if (event.type == 4) 4 else 8), buffer)
                        xlib.XFlush(display)
                    }
                }
            }
            timer.start()
            return registration
        } catch (error: Throwable) {
            xlib.XCloseDisplay(display)
            throw error
        }
    }
}
