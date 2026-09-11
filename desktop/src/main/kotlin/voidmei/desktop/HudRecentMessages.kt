package voidmei.desktop

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import voidmei.telemetry.*

@Composable
internal fun HudRecentMessages(state: HudMessageState?, kinds: Set<HudMessageKind> = HudMessageKind.entries.toSet(), limit: Int = 5, maxLines: Int = 0) {
    require(limit in 1..20)
    require(maxLines in 0..10)
    val colors = LocalReadingColors.current
    val labelColor = colors.label ?: LocalContentColor.current
    HudOverlayText("最近接收的游戏消息 · 最多 $limit 条", color = labelColor)
    if (kinds.isEmpty()) { HudOverlayText("未选择消息类别", color = labelColor); return }
    if (state == null) { HudOverlayText("等待游戏消息", color = labelColor); return }
    state.error?.let { HudOverlayText("消息更新失败（保留已有记录）", color = colors.warning ?: androidx.compose.material3.MaterialTheme.colorScheme.error) }
    val messages = state.messages.filter { it.kind in kinds }
    if (messages.isEmpty()) HudOverlayText(if (kinds.size == HudMessageKind.entries.size) "尚无游戏消息" else "尚无所选类别的消息", color = labelColor)
    messages.takeLast(limit).asReversed().forEach { message ->
        HudOverlayText("${if (message.kind == HudMessageKind.EVENT) "事件" else "损伤"} #${message.id} · ${message.text}",
            color = colors.value ?: LocalContentColor.current,
            maxLines = if (maxLines == 0) Int.MAX_VALUE else maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
