package voidmei.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/** Reads the foreground executable with limited query access; never records paths or titles. */
internal object WindowsForegroundProcess : ForegroundProcessSource {
    private interface User32 : StdCallLibrary {
        fun GetForegroundWindow(): Pointer?
        fun GetWindowThreadProcessId(window: Pointer, processId: IntByReference): Int
    }
    private interface Kernel32 : StdCallLibrary {
        fun OpenProcess(access: Int, inherit: Int, processId: Int): Pointer?
        fun QueryFullProcessImageNameW(process: Pointer, flags: Int, name: CharArray, size: IntByReference): Int
        fun CloseHandle(handle: Pointer): Int
    }
    private val user32 by lazy { Native.load("user32", User32::class.java) }
    private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

    override fun activeWindow(): Long? = user32.GetForegroundWindow()?.let(Pointer::nativeValue)?.takeIf { it != 0L }

    override fun processId(window: Long): Int? {
        val pid = IntByReference()
        return if (user32.GetWindowThreadProcessId(Pointer(window), pid) == 0) null else pid.value
    }

    override fun imageName(processId: Int): String? {
        val api = kernel32
        val process = api.OpenProcess(0x1000, 0, processId) ?: return null
        try {
            val buffer = CharArray(32768)
            val size = IntByReference(buffer.size)
            if (api.QueryFullProcessImageNameW(process, 0, buffer, size) == 0 ||
                size.value !in 1 until buffer.size) return null
            return String(buffer, 0, size.value)
        } finally {
            check(api.CloseHandle(process) != 0) { "Cannot close foreground process query handle" }
        }
    }
}
