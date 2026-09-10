package voidmei.desktop

import com.sun.jna.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Files
import java.nio.file.Path

/** A private XCB connection keeps stale-window errors out of AWT's global Xlib error handler. */
internal class X11ForegroundProcess(
    private val procRoot: Path = Path.of("/proc"),
    private val hostname: String = Files.readString(Path.of("/proc/sys/kernel/hostname")).trim(),
) : ForegroundProcessSource, AutoCloseable {
    @Structure.FieldOrder("sequence")
    class Cookie : Structure(), Structure.ByValue { @JvmField var sequence: Int = 0 }
    @Structure.FieldOrder("data", "remaining", "index")
    class Screens : Structure(), Structure.ByValue {
        @JvmField var data: Pointer? = null
        @JvmField var remaining: Int = 0
        @JvmField var index: Int = 0
    }
    private interface Xcb : Library {
        fun xcb_connect(display: String?, screen: IntByReference): Pointer
        fun xcb_connection_has_error(connection: Pointer): Int
        fun xcb_disconnect(connection: Pointer)
        fun xcb_get_setup(connection: Pointer): Pointer
        fun xcb_setup_roots_iterator(setup: Pointer): Screens
        fun xcb_screen_next(iterator: Pointer)
        fun xcb_intern_atom(connection: Pointer, onlyExisting: Byte, length: Short, name: ByteArray): Cookie
        fun xcb_intern_atom_reply(connection: Pointer, cookie: Cookie, error: PointerByReference): Pointer?
        fun xcb_get_property(connection: Pointer, delete: Byte, window: Int, property: Int, type: Int, offset: Int, length: Int): Cookie
        fun xcb_get_property_reply(connection: Pointer, cookie: Cookie, error: PointerByReference): Pointer?
        fun xcb_get_property_value(reply: Pointer): Pointer
        fun xcb_get_property_value_length(reply: Pointer): Int
    }
    private interface LibC : Library { fun free(pointer: Pointer) }
    private val xcb = Native.load("xcb", Xcb::class.java)
    private val libc = Native.load(Platform.C_LIBRARY_NAME, LibC::class.java)
    private val screen = IntByReference()
    private val connection = xcb.xcb_connect(null, screen)
    private val root: Long
    private var closed = false
    private val atoms = mutableMapOf<String, Int>()

    init {
        try {
            check(xcb.xcb_connection_has_error(connection) == 0) { "Cannot open X11 focus connection" }
            val screens = xcb.xcb_setup_roots_iterator(xcb.xcb_get_setup(connection))
            // xcb_screen_next takes a pointer, whereas roots_iterator returns the structure by value.
            repeat(screen.value) {
                check(screens.remaining > 1)
                // Use the native iterator through its pointer to account for variable-depth screen records.
                screenNext(screens)
            }
            check(screens.remaining > 0)
            root = screens.data!!.getInt(0).toLong() and 0xffffffffL
        } catch (failure: Throwable) { xcb.xcb_disconnect(connection); throw failure }
    }

    private fun screenNext(screens: Screens) {
        screens.write()
        xcb.xcb_screen_next(screens.pointer)
        screens.read()
    }

    private fun atom(name: String): Int? = atoms[name] ?: run {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        val error = PointerByReference()
        val reply = xcb.xcb_intern_atom_reply(connection, xcb.xcb_intern_atom(connection, 1, bytes.size.toShort(), bytes), error)
        try {
            if (error.value != null || reply == null) null else reply.getInt(8).takeIf { it != 0 }?.also { atoms[name] = it }
        } finally { reply?.let(libc::free); error.value?.let(libc::free) }
    }

    private fun property(window: Long, name: String, type: Int, format: Int, maxBytes: Int): ByteArray? {
        if (closed || window !in 1..0xffffffffL || xcb.xcb_connection_has_error(connection) != 0) return null
        val key = atom(name) ?: return null
        val error = PointerByReference()
        val reply = xcb.xcb_get_property_reply(connection,
            xcb.xcb_get_property(connection, 0, window.toInt(), key, type, 0, (maxBytes + 3) / 4), error)
        try {
            if (error.value != null || reply == null || reply.getInt(8) != type ||
                reply.getByte(1).toInt() != format || reply.getInt(12) != 0) return null
            val length = xcb.xcb_get_property_value_length(reply)
            return if (length in 1..maxBytes) xcb.xcb_get_property_value(reply).getByteArray(0, length) else null
        } finally { reply?.let(libc::free); error.value?.let(libc::free) }
    }

    private fun cardinal(window: Long, name: String, type: Int): Long? =
        property(window, name, type, 32, 4)?.takeIf { it.size == 4 }?.let {
            java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.nativeOrder()).int.toLong() and 0xffffffffL
        }

    override fun activeWindow(): Long? = cardinal(root, "_NET_ACTIVE_WINDOW", 33)?.takeIf { it != 0L }
    override fun processId(window: Long): Int? {
        val machine = property(window, "WM_CLIENT_MACHINE", 31, 8, 256)?.toString(Charsets.US_ASCII)?.trimEnd('\u0000')
        // Remote X clients have unrelated PIDs; never look these up in local /proc.
        if (hostname.isBlank() || !machine.equals(hostname, ignoreCase = true)) return null
        return cardinal(window, "_NET_WM_PID", 6)?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
    }
    override fun imageName(processId: Int): String? {
        if (processId <= 0) return null
        val executable = Files.readSymbolicLink(procRoot.resolve("$processId/exe"))
        // A Wine loader does not identify the Windows application running inside it.
        if (executable.fileName.toString() in setOf("wine", "wine64", "wine-preloader", "wine64-preloader")) return null
        return executable.toString()
    }

    override fun close() { if (!closed) { closed = true; xcb.xcb_disconnect(connection) } }
}
