package voidmei.desktop

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue
import java.awt.Window

internal class WindowsPointerRegion(private val window: Window) {
    private interface User32 : StdCallLibrary {
        // GWL_EXSTYLE is a 32-bit LONG even on 64-bit Windows; HWND remains pointer-sized.
        fun GetWindowLongW(window: Pointer, index: Int): Int
        fun SetWindowLongW(window: Pointer, index: Int, value: Int): Int
        fun SetWindowPos(window: Pointer, after: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Int
    }

    private val api by lazy { Native.load("user32", User32::class.java) }
    private fun handle(): Pointer = Native.getWindowPointer(window)
    private val style = WindowsPointerStyle(
        read = {
            val handle = handle()
            val functions = api
            Native.setLastError(0)
            val value = functions.GetWindowLongW(handle, -20)
            val error = Native.getLastError()
            check(value != 0 || error == 0) { "GetWindowLongW failed: $error" }
            value
        },
        write = { value ->
            val handle = handle()
            val functions = api
            Native.setLastError(0)
            val previous = functions.SetWindowLongW(handle, -20, value)
            val error = Native.getLastError()
            check(previous != 0 || error == 0) { "SetWindowLongW failed: $error" }
            // Refresh cached styles without moving, resizing, activating, or changing Z order.
            check(functions.SetWindowPos(handle, null, 0, 0, 0, 0, 0x0037) != 0) {
                "SetWindowPos failed: ${Native.getLastError()}"
            }
        },
    )

    fun setClickThrough(enabled: Boolean) {
        check(EventQueue.isDispatchThread()) { "Window input changes require the AWT event thread" }
        check(Platform.isWindows()) { "Windows pointer styles require Windows" }
        check(window.isDisplayable) { "HUD window has no native peer" }
        style.set(enabled)
    }
}
