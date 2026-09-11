package voidmei.desktop

import androidx.compose.runtime.*

internal data class RecordingNotice(val title: String, val message: String)

/** Consumes state changes even when no tray is available; never queues stale notifications. */
internal class RecordingNotifications {
    private var initialized = false
    private var directory: String? = null
    private var saved: RecordedFiles? = null

    fun update(state: RecordingState, lastSaved: RecordedFiles?): List<RecordingNotice> {
        val notices = mutableListOf<RecordingNotice>()
        if (!initialized) { initialized = true; saved = lastSaved }
        if (lastSaved != null && lastSaved != saved) {
            saved = lastSaved
            notices += RecordingNotice("飞行记录已保存", "飞行 CSV：${lastSaved.flight}\n发动机 CSV：${lastSaved.engines}")
        }
        val next = (state as? RecordingState.Active)?.directory
        if (next != null && next != directory)
            notices += RecordingNotice("已开启飞行记录", "目录：$next\n收到飞行数据后开始写入 CSV。")
        directory = next
        return notices
    }
}

@Composable
internal fun RecordingNotificationEffect(state: RecordingState, saved: RecordedFiles?, onNotice: (RecordingNotice) -> Unit) {
    val notifications = remember { RecordingNotifications() }
    val callback by rememberUpdatedState(onNotice)
    val directory = (state as? RecordingState.Active)?.directory
    LaunchedEffect(directory, saved) {
        notifications.update(state, saved).forEach(callback)
    }
}
