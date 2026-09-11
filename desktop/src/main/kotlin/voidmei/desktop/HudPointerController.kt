package voidmei.desktop

/** Retains an uncertain native state so a failed enable can still be undone. */
internal class HudPointerController(
    private val change: (Boolean) -> Unit,
    private val report: (String?) -> Unit,
) {
    var needsRestore: Boolean = false
        private set

    fun set(enabled: Boolean): Boolean {
        if (!enabled && !needsRestore) return true
        // A native operation can change the region and then fail while checking its result.
        needsRestore = true
        try {
            change(enabled)
            needsRestore = enabled
        } catch (e: Exception) {
            report("无法更改 HUD 鼠标穿透：${e.message}。请关闭穿透选项（分区模式需先返回纵向 HUD 布局）；若 HUD 仍不能操作，请关闭并重新打开 HUD。")
            return false
        } catch (e: LinkageError) {
            report("无法加载 HUD 鼠标穿透支持：${e.message}。请关闭穿透选项（分区模式需先返回纵向 HUD 布局）；若 HUD 仍不能操作，请关闭并重新打开 HUD。")
            return false
        }
        report(null)
        return true
    }
}
