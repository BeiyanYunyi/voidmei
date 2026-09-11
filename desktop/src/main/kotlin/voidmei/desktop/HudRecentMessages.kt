package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import voidmei.telemetry.*

@Composable
internal fun HudRecentMessages(state: HudMessageState?) {
    Text("最近接收的游戏消息 · 最多 5 条")
    if (state == null) { Text("等待游戏消息"); return }
    state.error?.let { Text("消息更新失败（保留已有记录）") }
    if (state.messages.isEmpty()) Text("尚无游戏消息")
    state.messages.takeLast(5).asReversed().forEach { message ->
        Text("${if (message.kind == HudMessageKind.EVENT) "事件" else "损伤"} #${message.id} · ${message.text}")
    }
}
