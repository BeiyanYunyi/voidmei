package voidmei.desktop

import androidx.compose.runtime.*
import voidmei.telemetry.ConnectionState

internal enum class ConnectionPhase(val description: String) {
    CONNECTING("正在连接 8111"), WAITING("8111 已连接，等待进入飞行"),
    FLYING("已收到飞行数据"), DISCONNECTED("8111 连接已断开，将继续重试");

    companion object {
        fun of(state: ConnectionState) = when (state) {
            ConnectionState.Connecting -> CONNECTING
            ConnectionState.WaitingForFlight -> WAITING
            is ConnectionState.Flying -> FLYING
            is ConnectionState.Disconnected -> DISCONNECTED
        }
    }
}

/** Advance even when notifications are disabled or unavailable; retries and samples are not events. */
internal class ConnectionNotifications {
    private var previous: ConnectionPhase? = null
    fun update(phase: ConnectionPhase): String? {
        if (previous == null) { previous = phase; return null }
        if (phase == previous) return null
        previous = phase
        return phase.description.takeUnless { phase == ConnectionPhase.CONNECTING }
    }
}

@Composable
internal fun ConnectionNotificationEffect(state: ConnectionState, endpoint: String, generation: Int,
    onNotice: (String, String) -> Unit) {
    val notices = remember(endpoint, generation) { ConnectionNotifications() }
    val callback by rememberUpdatedState(onNotice)
    val phase = ConnectionPhase.of(state)
    LaunchedEffect(notices, phase) {
        notices.update(phase)?.let { callback(it, "服务地址：$endpoint") }
    }
}
