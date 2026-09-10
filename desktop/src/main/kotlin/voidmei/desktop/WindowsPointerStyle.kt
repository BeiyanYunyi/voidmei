package voidmei.desktop

/** Owns only WS_EX_TRANSPARENT; the AWT rendering path owns WS_EX_LAYERED. */
internal class WindowsPointerStyle(private val read: () -> Int, private val write: (Int) -> Unit) {
    private var originalTransparent: Int? = null

    fun set(enabled: Boolean) {
        if (!enabled && originalTransparent == null) return
        val current = read()
        if (enabled) {
            check(current and LAYERED != 0) { "HUD is not a Windows layered window" }
            if (originalTransparent == null) originalTransparent = current and TRANSPARENT
        }
        val transparent = if (enabled) TRANSPARENT else checkNotNull(originalTransparent)
        write((current and TRANSPARENT.inv()) or transparent)
        val actual = read()
        check(actual and TRANSPARENT == transparent && (!enabled || actual and LAYERED != 0)) {
            "Windows did not apply the requested HUD input style"
        }
        if (!enabled) originalTransparent = null
    }

    companion object {
        const val TRANSPARENT = 0x00000020
        const val LAYERED = 0x00080000
    }
}
