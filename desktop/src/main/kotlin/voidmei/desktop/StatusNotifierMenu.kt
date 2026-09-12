package voidmei.desktop

import com.sun.jna.Pointer
import voidmei.desktop.TrayDBus.child
import voidmei.desktop.TrayDBus.integer
import voidmei.desktop.TrayDBus.integers
import voidmei.desktop.TrayDBus.quote
import voidmei.desktop.TrayDBus.string
import voidmei.desktop.TrayDBus.strings

/** DBusMenu exposes menu data; the desktop host supplies fonts, theme, spacing and scaling. */
internal class StatusNotifierMenu(private val activate: (Int) -> Unit) {
    private val items = linkedMapOf(
        0 to mapOf("children-display" to quote("submenu")),
        1 to mapOf("label" to quote("显示主窗口"), "enabled" to "true", "visible" to "true"),
        2 to mapOf("label" to quote("切换 HUD"), "enabled" to "true", "visible" to "true"),
        3 to mapOf("type" to quote("separator")),
        4 to mapOf("label" to quote("退出 VoidMei"), "enabled" to "true", "visible" to "true"),
    )
    fun property(name: String): String = when (name) {
        "Version" -> "uint32 3"
        "TextDirection" -> quote("ltr")
        "Status" -> quote("normal")
        "IconThemePath" -> "@as []"
        else -> error("Unknown menu property: $name")
    }
    private fun properties(id: Int, names: List<String>): String =
        "@a{sv} {" + items.getValue(id).filterKeys { names.isEmpty() || it in names }
            .entries.joinToString { (key, value) -> "${quote(key)}: <$value>" } + "}"
    private fun layout(id: Int, depth: Int, names: List<String>): String {
        val children = if (id == 0 && depth != 0) (1..4).joinToString { "<${layout(it, 0, names)}>" } else ""
        return "($id, ${properties(id, names)}, @av [$children])"
    }
    private fun event(parameters: Pointer): Boolean {
        val id = integer(parameters, 0)
        if (id !in items) return false
        if (string(parameters, 1) == "clicked" && id in listOf(1, 2, 4)) activate(id)
        return true
    }
    fun method(name: String, parameters: Pointer): String = when (name) {
        "GetLayout" -> "(uint32 1, ${layout(integer(parameters, 0), integer(parameters, 1), child(parameters, 2, ::strings))})"
        "GetGroupProperties" -> {
            val ids = child(parameters, 0, ::integers).ifEmpty { items.keys.toList() }
            val names = child(parameters, 1, ::strings)
            "(@a(ia{sv}) [" + ids.filter { it in items }.joinToString { "($it, ${properties(it, names)})" } + "],)"
        }
        "GetProperty" -> "(<${items.getValue(integer(parameters, 0)).getValue(string(parameters, 1))}>,)"
        "Event" -> { require(event(parameters)) { "Unknown menu item" }; "()" }
        "EventGroup" -> {
            val errors = child(parameters, 0) { events ->
                (0 until TrayDBus.glib.g_variant_n_children(events).toInt()).mapNotNull { index ->
                    child(events, index) { if (event(it)) null else integer(it, 0) }
                }
            }
            "(@ai [${errors.joinToString()}],)"
        }
        "AboutToShow" -> { require(integer(parameters, 0) in items); "(false,)" }
        "AboutToShowGroup" -> {
            val errors = child(parameters, 0, ::integers).filter { it !in items }
            "(@ai [], @ai [${errors.joinToString()}])"
        }
        else -> error("Unknown menu method: $name")
    }
}
