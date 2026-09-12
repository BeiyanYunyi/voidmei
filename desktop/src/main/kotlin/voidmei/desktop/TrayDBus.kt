package voidmei.desktop

import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference

/** Small GIO binding shared by the Linux tray and its isolated D-Bus tests. */
internal object TrayDBus {
    interface Gio : Library {
        fun g_dbus_address_get_for_bus_sync(type: Int, cancellable: Pointer?, error: PointerByReference): Pointer?
        fun g_dbus_connection_new_for_address_sync(address: String, flags: Int, observer: Pointer?, cancellable: Pointer?, error: PointerByReference): Pointer?
        fun g_dbus_connection_set_exit_on_close(connection: Pointer, enabled: Int)
        fun g_dbus_connection_is_closed(connection: Pointer): Int
        fun g_dbus_connection_get_unique_name(connection: Pointer): String
        fun g_dbus_connection_close_sync(connection: Pointer, cancellable: Pointer?, error: PointerByReference?): Int
        fun g_dbus_connection_call_sync(connection: Pointer, bus: String, path: String, iface: String, method: String,
            parameters: Pointer?, replyType: Pointer?, flags: Int, timeout: Int, cancellable: Pointer?, error: PointerByReference): Pointer?
        fun g_dbus_node_info_new_for_xml(xml: String, error: PointerByReference): Pointer?
        fun g_dbus_node_info_lookup_interface(node: Pointer, name: String): Pointer?
        fun g_dbus_node_info_unref(node: Pointer)
        fun g_dbus_connection_register_object(connection: Pointer, path: String, info: Pointer, vtable: VTable,
            data: Pointer?, destroy: Pointer?, error: PointerByReference): Int
        fun g_dbus_connection_unregister_object(connection: Pointer, id: Int): Int
        fun g_dbus_method_invocation_return_value(invocation: Pointer, parameters: Pointer?)
        fun g_dbus_method_invocation_return_dbus_error(invocation: Pointer, name: String, message: String)
        fun g_dbus_connection_signal_subscribe(connection: Pointer, sender: String?, iface: String, member: String,
            path: String?, arg0: String?, flags: Int, callback: Signal, data: Pointer?, destroy: Pointer?): Int
        fun g_dbus_connection_signal_unsubscribe(connection: Pointer, id: Int)
    }
    interface GLib : Library {
        fun g_main_context_new(): Pointer
        fun g_main_context_push_thread_default(context: Pointer)
        fun g_main_context_pop_thread_default(context: Pointer)
        fun g_main_context_iteration(context: Pointer, mayBlock: Int): Int
        fun g_main_context_unref(context: Pointer)
        fun g_variant_parse(type: Pointer?, text: String, limit: Pointer?, end: Pointer?, error: PointerByReference): Pointer?
        fun g_variant_unref(value: Pointer)
        fun g_variant_n_children(value: Pointer): NativeLong
        fun g_variant_get_child_value(value: Pointer, index: NativeLong): Pointer
        fun g_variant_get_int32(value: Pointer): Int
        fun g_variant_get_string(value: Pointer, length: Pointer?): String
        fun g_variant_print(value: Pointer, annotate: Int): Pointer
        fun g_error_free(error: Pointer)
        fun g_free(pointer: Pointer)
    }
    interface GObject : Library { fun g_object_unref(value: Pointer) }
    fun interface Method : Callback {
        fun invoke(connection: Pointer, sender: String, path: String, iface: String, method: String,
            parameters: Pointer, invocation: Pointer, data: Pointer?)
    }
    fun interface Property : Callback {
        fun invoke(connection: Pointer, sender: String, path: String, iface: String, property: String,
            error: Pointer?, data: Pointer?): Pointer?
    }
    fun interface Signal : Callback {
        fun invoke(connection: Pointer, sender: String, path: String, iface: String, name: String, parameters: Pointer, data: Pointer?)
    }
    @Structure.FieldOrder("method", "property", "setter", "padding")
    class VTable : Structure() {
        @JvmField var method: Method? = null
        @JvmField var property: Property? = null
        @JvmField var setter: Pointer? = null
        @JvmField var padding = arrayOfNulls<Pointer>(8)
    }
    val gio: Gio by lazy { Native.load("gio-2.0", Gio::class.java) }
    val glib: GLib by lazy { Native.load("glib-2.0", GLib::class.java) }
    val objects: GObject by lazy { Native.load("gobject-2.0", GObject::class.java) }

    fun <T : Any> checked(error: PointerByReference, result: T?): T {
        error.value?.let {
            val message = it.getPointer(8).getString(0)
            glib.g_error_free(it)
            error.value = null
            error(message)
        }
        return checkNotNull(result) { "GIO returned no result" }
    }
    fun connect(): Pointer {
        val error = PointerByReference()
        val address = checked(error, gio.g_dbus_address_get_for_bus_sync(2, null, error)) // session bus
        val connection = try {
            checked(error, gio.g_dbus_connection_new_for_address_sync(address.getString(0), 9, null, null, error))
        } finally { glib.g_free(address) }
        gio.g_dbus_connection_set_exit_on_close(connection, 0)
        return connection
    }
    /** g_variant_parse returns a full (non-floating) reference. */
    fun variant(text: String): Pointer {
        val error = PointerByReference()
        return checked(error, glib.g_variant_parse(null, text, null, null, error))
    }
    fun <T> withVariant(text: String, action: (Pointer) -> T): T {
        val value = variant(text)
        return try { action(value) } finally { glib.g_variant_unref(value) }
    }
    fun <T> child(value: Pointer, index: Int, action: (Pointer) -> T): T {
        val child = glib.g_variant_get_child_value(value, NativeLong(index.toLong()))
        return try { action(child) } finally { glib.g_variant_unref(child) }
    }
    fun integer(value: Pointer, index: Int) = child(value, index, glib::g_variant_get_int32)
    fun string(value: Pointer, index: Int) = child(value, index) { glib.g_variant_get_string(it, null) }
    fun strings(value: Pointer) = (0 until glib.g_variant_n_children(value).toInt()).map { string(value, it) }
    fun integers(value: Pointer) = (0 until glib.g_variant_n_children(value).toInt()).map { integer(value, it) }
    fun print(value: Pointer): String {
        val text = glib.g_variant_print(value, 1)
        return try { text.getString(0) } finally { glib.g_free(text) }
    }
    fun call(connection: Pointer, bus: String, path: String, iface: String, method: String, parameters: String): String =
        withVariant(parameters) { args ->
            val error = PointerByReference()
            val reply = checked(error, gio.g_dbus_connection_call_sync(connection, bus, path, iface, method, args, null, 0, 1500, null, error))
            try { print(reply) } finally { glib.g_variant_unref(reply) }
        }
    fun reply(invocation: Pointer, text: String) = withVariant(text) { gio.g_dbus_method_invocation_return_value(invocation, it) }
    fun quote(text: String) = "'" + buildString {
        for (c in text) append(when (c) {
            '\\' -> "\\\\"
            '\'' -> "\\'"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (c.code < 32) "\\u%04x".format(c.code) else c.toString()
        })
    } + "'"
}
