package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.telemetry.*

@Composable
internal fun HudMessagesPanel(endpoint: String, flight: ConnectionState.Flying?) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起游戏消息" else "查看游戏消息") }
    if (!expanded) return
    if (flight == null) { Text("进入飞行后读取游戏消息"); return }
    var reload by remember(endpoint, flight.telemetry.aircraft) { mutableStateOf(0) }
    var state by remember(endpoint, flight.telemetry.aircraft, reload) { mutableStateOf(HudMessageState(emptyList())) }
    LaunchedEffect(endpoint, flight.telemetry.aircraft, reload) {
        try {
            HttpTelemetryTransport(endpoint).use { transport ->
                HudMessagePoller(transport).states().collect { state = it }
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { state = state.copy(error = e.message ?: "消息不可用") }
    }
    TextButton(onClick = { reload++ }) { Text("重新读取游戏消息") }
    Text("重新读取会清空本面板历史并从零游标获取，用于游戏消息编号重置后的恢复。", style = MaterialTheme.typography.bodySmall)
    key(endpoint, flight.telemetry.aircraft, reload) { HudMessageList(state) }
}

@Composable
internal fun HudMessageList(state: HudMessageState) {
    Text("游戏事件与损伤消息 · 最多保留 200 条；编号按类别独立，不代表跨类别时间顺序。", style = MaterialTheme.typography.bodySmall)
    state.error?.let { Text("消息更新失败：$it（保留已有记录）", color = MaterialTheme.colorScheme.error) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    if (state.messages.isEmpty()) Text("尚无游戏消息")
    else TextButton(onClick = { scope.launch { listState.scrollToItem(0) } }) { Text("回到最新消息") }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("game-message-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.messages.asReversed(), key = { "${it.kind}:${it.id}" }) {
            Text("${if (it.kind == HudMessageKind.EVENT) "事件" else "损伤"} #${it.id} · ${it.text}")
        }
    }
}
