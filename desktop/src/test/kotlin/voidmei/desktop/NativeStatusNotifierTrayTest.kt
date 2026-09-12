package voidmei.desktop

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Test
import kotlin.test.*
import voidmei.desktop.TrayDBus.call
import voidmei.desktop.TrayDBus.gio
import voidmei.desktop.TrayDBus.glib

class NativeStatusNotifierTrayTest {
    @Test fun desktopReceivesNativeMenuActionsNotificationsAndReregistration() {
        require(System.getenv("VOIDMEI_TEST_ISOLATED_DBUS") == "1")
        var host = TestHost()
        val actions = CopyOnWriteArrayList<String>()
        val availability = CopyOnWriteArrayList<Boolean>()
        var tray: StatusNotifierTray? = null
        val client = TrayDBus.connect()
        try {
            EventQueue.invokeAndWait {
                tray = assertIs<StatusNotifierTray>(installDesktopTray(
                    { assertTrue(EventQueue.isDispatchThread()); actions += "show" },
                    { actions += "hud" }, { actions += "exit" }, { availability += it }))
            }
            val active = assertNotNull(tray)
            await { host.registrations.contains(active.serviceName) }
            fun invoke(path: String, iface: String, method: String, parameters: String) =
                call(client, active.serviceName, path, iface, method, parameters)
            val props = invoke("/StatusNotifierItem", "org.freedesktop.DBus.Properties", "GetAll", "('org.kde.StatusNotifierItem',)")
            assertTrue(props.contains("'/Menu'"), props)
            assertTrue(props.contains("'ItemIsMenu': <false>"), props)
            assertTrue(props.contains("'IconPixmap'"), props)
            val layout = invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "GetLayout", "(0, -1, @as [])")
            for (label in listOf("显示主窗口", "切换 HUD", "退出 VoidMei", "separator")) assertTrue(label in layout, layout)
            val filtered = invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "GetLayout", "(0, 1, ['label'])")
            assertFalse("enabled" in filtered)
            assertFalse("显示主窗口" in invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "GetLayout", "(0, 0, @as [])"))
            invoke("/StatusNotifierItem", StatusNotifierTray.ITEM_INTERFACE, "Activate", "(100, 100)")
            for (id in listOf(1, 2, 4)) invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "Event", "($id, 'clicked', <0>, uint32 0)")
            await { actions.size == 4 }
            assertEquals(listOf("show", "show", "hud", "exit"), actions.toList())
            val grouped = invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "EventGroup", "(@a(isvu) [(2, 'clicked', <0>, 0), (99, 'clicked', <0>, 0)],)")
            assertTrue("99" in grouped)
            await { actions.size == 5 }
            assertEquals("hud", actions.last())
            assertEquals("(false,)", invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "AboutToShow", "(0,)"))
            val title = "引号 ' 与 \\"
            val body = "第一行\n第二行"
            active.showMessage(title, body)
            await { host.notices.isNotEmpty() }
            assertEquals(title to body, host.notices.single())
            host.close()
            await { false in availability }
            host = TestHost()
            await { host.registrations.contains(active.serviceName) && availability.lastOrNull() == true }
            val count = actions.size
            EventQueue.invokeAndWait {
                invoke("/Menu", StatusNotifierTray.MENU_INTERFACE, "Event", "(2, 'clicked', <0>, uint32 0)")
                active.close()
            }
            await { !call(client, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "NameHasOwner",
                "('${active.serviceName}',)").contains("true") }
            EventQueue.invokeAndWait { assertEquals(count, actions.size, "Disposed tray must ignore queued actions") }
        } finally {
            tray?.close()
            host.close()
            gio.g_dbus_connection_close_sync(client, null, null)
            TrayDBus.objects.g_object_unref(client)
        }
    }

    @Test fun absentWatcherDoesNotClaimTrayAvailability() {
        require(System.getenv("VOIDMEI_TEST_ISOLATED_DBUS") == "1")
        assertNull(StatusNotifierTray.install(desktopTrayImage(), {}, {}, {}, {}))
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < end) Thread.sleep(10)
        assertTrue(condition(), "Timed out waiting for native D-Bus event")
    }

    /** A dedicated bus replaces the real panel and notification daemon in this test. */
    private class TestHost : AutoCloseable {
        val registrations = CopyOnWriteArrayList<String>()
        val notices = CopyOnWriteArrayList<Pair<String, String>>()
        private val closed = AtomicBoolean()
        private val ready = CompletableFuture<Unit>()
        private val table = TrayDBus.VTable().apply {
            method = TrayDBus.Method { _, _, _, _, method, args, invocation, _ ->
                when (method) {
                    "RegisterStatusNotifierItem" -> {
                        registrations += TrayDBus.string(args, 0)
                        TrayDBus.reply(invocation, "()")
                    }
                    "Notify" -> {
                        notices += TrayDBus.string(args, 3) to TrayDBus.string(args, 4)
                        TrayDBus.reply(invocation, "(uint32 1,)")
                    }
                }
            }
        }
        private val thread = Thread({
            val context = glib.g_main_context_new()
            glib.g_main_context_push_thread_default(context)
            var connection: Pointer? = null
            var node: Pointer? = null
            try {
                connection = TrayDBus.connect()
                val error = PointerByReference()
                node = TrayDBus.checked(error, gio.g_dbus_node_info_new_for_xml("""
                    <node>
                    <interface name="org.kde.StatusNotifierWatcher"><method name="RegisterStatusNotifierItem"><arg type="s" direction="in"/></method></interface>
                    <interface name="org.freedesktop.Notifications"><method name="Notify">
                    <arg type="s" direction="in"/><arg type="u" direction="in"/><arg type="s" direction="in"/><arg type="s" direction="in"/><arg type="s" direction="in"/>
                    <arg type="as" direction="in"/><arg type="a{sv}" direction="in"/><arg type="i" direction="in"/><arg type="u" direction="out"/>
                    </method></interface>
                    </node>
                """.trimIndent(), error))
                for ((path, iface) in listOf("/StatusNotifierWatcher" to StatusNotifierTray.WATCHER, "/org/freedesktop/Notifications" to "org.freedesktop.Notifications")) {
                    val info = checkNotNull(gio.g_dbus_node_info_lookup_interface(node, iface))
                    assertNotEquals(0, TrayDBus.checked(error, gio.g_dbus_connection_register_object(connection, path, info, table, null, null, error)))
                    assertTrue("1" in call(connection, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "RequestName", "('$iface', uint32 0)"))
                }
                ready.complete(Unit)
                while (!closed.get()) {
                    while (glib.g_main_context_iteration(context, 0) != 0) { }
                    Thread.sleep(10)
                }
            } catch (e: Throwable) { ready.completeExceptionally(e) }
            finally {
                connection?.let { gio.g_dbus_connection_close_sync(it, null, null); TrayDBus.objects.g_object_unref(it) }
                node?.let(gio::g_dbus_node_info_unref)
                glib.g_main_context_pop_thread_default(context)
                glib.g_main_context_unref(context)
            }
        }, "Test desktop host").apply { isDaemon = true; start() }
        init { ready.get(5, TimeUnit.SECONDS) }
        override fun close() { closed.set(true); thread.join(3000); check(!thread.isAlive) }
    }
}
