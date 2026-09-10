package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Composable
internal fun PollingIntervalSettings(intervalMs: Long, onChange: (Long) -> Unit) {
    var draft by remember(intervalMs) { mutableStateOf(intervalMs.toString()) }
    var error by remember(intervalMs) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("刷新间隔 $intervalMs ms")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50L, 100L, 250L, 500L).forEach { interval ->
                FilterChip(intervalMs == interval, {
                    draft = interval.toString(); error = null; onChange(interval)
                }, label = { Text("$interval ms") })
            }
        }
        OutlinedTextField(draft, { draft = it; error = null }, singleLine = true,
            label = { Text("自定义刷新间隔 (ms)") }, isError = error != null)
        TextButton(onClick = {
            val value = draft.trim().toLongOrNull()?.takeIf { it in 20..5000 }
            if (value == null) error = "请输入 20–5000 范围内的整数毫秒"
            else { draft = value.toString(); error = null; onChange(value) }
        }) { Text("应用刷新间隔") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (intervalMs > 2000) Text("超过 2 秒的采样间隔会使 SEP、加速度和燃油续航等连续采样估算不可用。",
            style = MaterialTheme.typography.bodySmall)
    }
}
