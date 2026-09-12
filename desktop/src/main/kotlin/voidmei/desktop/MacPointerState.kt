package voidmei.desktop

/** Preserve the native input policy, including after a write succeeds but verification fails. */
internal class MacPointerState {
    private var saved: Pair<Long, Boolean>? = null

    fun set(handle: Long, enabled: Boolean, read: () -> Boolean, write: (Boolean) -> Unit) {
        require(handle != 0L) { "HUD window has no native peer" }
        if (saved?.first != handle) saved = null // A recreated AWT peer has its own policy.
        if (enabled && saved == null) saved = handle to read()
        val desired = if (enabled) true else saved?.second ?: return
        write(desired)
        check(read() == desired) { "macOS did not apply the HUD mouse input policy" }
        if (!enabled) saved = null
    }
}
