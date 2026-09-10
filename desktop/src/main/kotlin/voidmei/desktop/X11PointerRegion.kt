package voidmei.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.EventQueue
import java.awt.Window

/** Only call for an application's own, realized window, on the AWT event thread. */
internal object X11PointerRegion {
    private interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XCloseDisplay(display: Pointer): Int
        fun XSync(display: Pointer, discard: Int): Int
        fun XFree(data: Pointer): Int
    }

    private interface Shape : Library {
        fun XShapeQueryVersion(display: Pointer, major: IntByReference, minor: IntByReference): Int
        fun XShapeCombineRectangles(display: Pointer, window: NativeLong, kind: Int,
            x: Int, y: Int, rectangles: Pointer?, count: Int, operation: Int, ordering: Int)
        fun XShapeCombineMask(display: Pointer, window: NativeLong, kind: Int,
            x: Int, y: Int, mask: NativeLong, operation: Int)
        fun XShapeGetRectangles(display: Pointer, window: NativeLong, kind: Int,
            count: IntByReference, ordering: IntByReference): Pointer?
    }

    private val xlib by lazy { Native.load("X11", Xlib::class.java) }
    private val shape by lazy { Native.load("Xext", Shape::class.java) }

    /** Empty input region passes pointer events through; None restores the default region. */
    fun setClickThrough(window: Window, enabled: Boolean) {
        check(EventQueue.isDispatchThread()) { "Window input changes require the AWT event thread" }
        check(Platform.isLinux()) { "X11 pointer regions require Linux" }
        check(window.isDisplayable) { "HUD window has no native peer" }
        val display = checkNotNull(xlib.XOpenDisplay(null)) { "Cannot open the X11 display" }
        try {
            val major = IntByReference()
            val minor = IntByReference()
            check(shape.XShapeQueryVersion(display, major, minor) != 0 &&
                (major.value > 1 || major.value == 1 && minor.value >= 1)) {
                "X11 SHAPE 1.1 input regions are unavailable"
            }
            val id = NativeLong(Native.getWindowID(window))
            check(id.toLong() != 0L) { "HUD native window ID is unavailable" }
            if (enabled) {
                shape.XShapeCombineRectangles(display, id, 2, 0, 0, null, 0, 0, 0)
            } else {
                shape.XShapeCombineMask(display, id, 2, 0, 0, NativeLong(0), 0)
            }
            xlib.XSync(display, 0)
            val count = IntByReference()
            val rectangles = shape.XShapeGetRectangles(display, id, 2, count, IntByReference())
            try {
                check(if (enabled) count.value == 0 else count.value > 0) {
                    "X11 did not apply the requested HUD input region"
                }
            } finally {
                if (rectangles != null) xlib.XFree(rectangles)
            }
        } finally {
            xlib.XCloseDisplay(display)
        }
    }
}
