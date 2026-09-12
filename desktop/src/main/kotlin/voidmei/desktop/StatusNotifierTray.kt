package voidmei.desktop

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import voidmei.desktop.TrayDBus.call
import voidmei.desktop.TrayDBus.checked
import voidmei.desktop.TrayDBus.gio
import voidmei.desktop.TrayDBus.glib
import voidmei.desktop.TrayDBus.quote

/** Linux native tray: the host draws DBusMenu instead of AWT drawing an unthemed X11 popup. */
internal class StatusNotifierTray private constructor(
    private val image: BufferedImage,
    private val onShow: () -> Unit,
    private val onHud: () -> Unit,
    private val onExit: () -> Unit,
    private val onAvailability: (Boolean) -> Unit,
) : DesktopTray {
    private val closed = AtomicBoolean()
    private val ready = CompletableFuture<Boolean>()
    private val work = ConcurrentLinkedQueue<() -> Unit>()
    private lateinit var connection: Pointer
    internal lateinit var serviceName: String
        private set
    private val menu = StatusNotifierMenu { id -> dispatch(when (id) { 1 -> onShow; 2 -> onHud; else -> onExit }) }
    private fun dispatch(action: () -> Unit) = EventQueue.invokeLater { if (!closed.get()) action() }

    private val vtable = TrayDBus.VTable().apply {
        method = TrayDBus.Method { _, _, _, iface, method, parameters, invocation, _ ->
            try {
                val response = if (iface == MENU_INTERFACE) menu.method(method, parameters) else {
                    when (method) {
                        "Activate", "SecondaryActivate" -> dispatch(onShow)
                        // Hosts supporting DBusMenu show Menu themselves. Other clients retain a recovery action.
                        "ContextMenu" -> dispatch(onShow)
                        "Scroll" -> Unit
                        else -> error("Unknown tray method: $method")
                    }
                    "()"
                }
                TrayDBus.reply(invocation, response)
            } catch (e: Exception) {
                gio.g_dbus_method_invocation_return_dbus_error(invocation,
                    "org.freedesktop.DBus.Error.InvalidArgs", e.message ?: "Invalid tray request")
            }
        }
        property = TrayDBus.Property { _, _, _, iface, name, _, _ ->
            TrayDBus.variant(if (iface == MENU_INTERFACE) menu.property(name) else properties.getValue(name))
        }
    }
    private val watcher = TrayDBus.Signal { _, _, _, _, _, parameters, _ ->
        try {
            val owner = TrayDBus.string(parameters, 2)
            if (owner.isEmpty()) dispatch { onAvailability(false) }
            else { registerWithHost(); dispatch { onAvailability(true) } }
        } catch (e: Exception) {
            println("[VoidMei tray] 托盘重新注册失败：${e.message}")
            dispatch { onAvailability(false) }
        }
    }
    private val properties by lazy {
        val pixels = buildList {
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                for (shift in listOf(24, 16, 8, 0)) add((argb ushr shift) and 255)
            }
        }.joinToString()
        mapOf(
            "Category" to quote("ApplicationStatus"), "Id" to quote("voidmei-kotlin"),
            "Title" to quote("VoidMei"), "Status" to quote("Active"), "WindowId" to "0", "IconThemePath" to quote(""),
            "IconName" to quote(""), "IconPixmap" to "@a(iiay) [(${image.width}, ${image.height}, @ay [$pixels])]",
            "OverlayIconName" to quote(""), "OverlayIconPixmap" to "@a(iiay) []",
            "AttentionIconName" to quote(""), "AttentionIconPixmap" to "@a(iiay) []", "AttentionMovieName" to quote(""),
            "ToolTip" to "('', @a(iiay) [], 'VoidMei', '飞行遥测与 HUD')",
            "ItemIsMenu" to "false", "Menu" to "objectpath '/Menu'",
        )
    }
    private fun registerWithHost() {
        call(connection, WATCHER, "/StatusNotifierWatcher", WATCHER, "RegisterStatusNotifierItem", "(${quote(serviceName)},)")
    }
    private fun run() {
        var context: Pointer? = null
        var bus: Pointer? = null
        var node: Pointer? = null
        var subscription = 0
        val registrations = mutableListOf<Int>()
        try {
            context = glib.g_main_context_new()
            glib.g_main_context_push_thread_default(context)
            connection = TrayDBus.connect()
            bus = connection
            serviceName = gio.g_dbus_connection_get_unique_name(connection)
            if (!call(connection, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                    "NameHasOwner", "('$WATCHER',)").contains("true")) {
                ready.complete(false)
                return
            }
            val xml = checkNotNull(javaClass.getResourceAsStream("/voidmei-tray.xml")).bufferedReader().use { it.readText() }
            val error = PointerByReference()
            node = checked(error, gio.g_dbus_node_info_new_for_xml(xml, error))
            for ((path, iface) in listOf("/StatusNotifierItem" to ITEM_INTERFACE, "/Menu" to MENU_INTERFACE)) {
                val info = checkNotNull(gio.g_dbus_node_info_lookup_interface(node, iface))
                val id = checked(error, gio.g_dbus_connection_register_object(connection, path, info, vtable, null, null, error))
                check(id != 0) { "Cannot export tray interface" }
                registrations += id
            }
            subscription = gio.g_dbus_connection_signal_subscribe(connection, "org.freedesktop.DBus", "org.freedesktop.DBus",
                "NameOwnerChanged", "/org/freedesktop/DBus", WATCHER, 0, watcher, null, null)
            registerWithHost()
            ready.complete(true)
            while (!closed.get()) {
                if (gio.g_dbus_connection_is_closed(connection) != 0) {
                    dispatch { onAvailability(false) }
                    break
                }
                while (!closed.get() && glib.g_main_context_iteration(context, 0) != 0) { /* dispatch GIO callbacks */ }
                while (!closed.get()) (work.poll() ?: break).invoke()
                Thread.sleep(15)
            }
        } catch (e: Exception) {
            if (!ready.isDone) ready.completeExceptionally(e)
            else { println("[VoidMei tray] 桌面托盘已断开：${e.message}"); dispatch { onAvailability(false) } }
        } catch (e: LinkageError) {
            ready.completeExceptionally(e)
        } finally {
            work.clear()
            bus?.let {
                if (subscription != 0) gio.g_dbus_connection_signal_unsubscribe(it, subscription)
                registrations.forEach { id -> gio.g_dbus_connection_unregister_object(it, id) }
                gio.g_dbus_connection_close_sync(it, null, null)
                TrayDBus.objects.g_object_unref(it)
            }
            node?.let(gio::g_dbus_node_info_unref)
            context?.let { glib.g_main_context_pop_thread_default(it); glib.g_main_context_unref(it) }
        }
    }
    override fun showMessage(title: String, message: String) {
        if (closed.get()) return
        work.add {
            try {
                call(connection, "org.freedesktop.Notifications", "/org/freedesktop/Notifications",
                    "org.freedesktop.Notifications", "Notify",
                    "('VoidMei', uint32 0, '', ${quote(title)}, ${quote(message)}, @as [], @a{sv} {}, -1)")
            } catch (e: Exception) { println("[VoidMei tray] 通知不可用：${e.message}") }
        }
    }
    override fun close() { closed.set(true) }

    companion object {
        internal const val WATCHER = "org.kde.StatusNotifierWatcher"
        internal const val ITEM_INTERFACE = "org.kde.StatusNotifierItem"
        internal const val MENU_INTERFACE = "com.canonical.dbusmenu"
        fun install(image: BufferedImage, onShow: () -> Unit, onHud: () -> Unit, onExit: () -> Unit,
            onAvailability: (Boolean) -> Unit): StatusNotifierTray? {
            if (!Platform.isLinux()) return null
            val tray = StatusNotifierTray(image, onShow, onHud, onExit, onAvailability)
            Thread(tray::run, "VoidMei desktop tray").apply { isDaemon = true; start() }
            return try {
                if (tray.ready.get(5, TimeUnit.SECONDS)) tray else { tray.close(); null }
            } catch (e: Exception) {
                tray.close()
                println("[VoidMei tray] 原生桌面菜单不可用，使用 AWT 托盘：${e.cause?.message ?: e.message}")
                null
            }
        }
    }
}
