package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import voidmei.telemetry.*

@Composable
internal fun HudRecentMessages(state: HudMessageState?, kinds: Set<HudMessageKind> = HudMessageKind.entries.toSet(), limit: Int = 5, maxLines: Int = 0) {
    require(limit in 1..20)
    require(maxLines in 0..10)
    Text("最近接收的游戏消息 · 最多 $limit 条")
    if (kinds.isEmpty()) { Text("未选择消息类别"); return }
    if (state == null) { Text("等待游戏消息"); return }
    state.error?.let { Text("消息更新失败（保留已有记录）") }
    val messages = state.messages.filter { it.kind in kinds }
    if (messages.isEmpty()) Text(if (kinds.size == HudMessageKind.entries.size) "尚无游戏消息" else "尚无所选类别的消息")
    messages.takeLast(limit).asReversed().forEach { message ->
        Text("${if (message.kind == HudMessageKind.EVENT) "事件" else "损伤"} #${message.id} · ${message.text}",
            maxLines = if (maxLines == 0) Int.MAX_VALUE else maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
