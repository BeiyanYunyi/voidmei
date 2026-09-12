package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.UnmigratedLegacySetting

@Composable
internal fun LegacyUnmigratedReport(entries: List<UnmigratedLegacySetting>) {
    if (entries.isEmpty()) return
    var expanded by remember(entries) { mutableStateOf(false) }
    var query by remember(entries) { mutableStateOf("") }
    var limit by remember(entries) { mutableIntStateOf(25) }
    TextButton(onClick = { expanded = !expanded }) {
        Text("未迁移 ${entries.size} 项：${if (expanded) "收起" else "查看"}")
    }
    if (!expanded) return
    val matches = remember(entries, query) {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        entries.filter { entry -> terms.all { term ->
            entry.label.contains(term, ignoreCase = true) || entry.target.contains(term, ignoreCase = true) || entry.sourcePath.any { it.contains(term, ignoreCase = true) }
        } }
    }
    OutlinedTextField(query, { query = it; limit = 25 }, Modifier.fillMaxWidth().testTag("legacy-unmigrated-search"),
        singleLine = true, label = { Text("搜索未迁移项目") },
        supportingText = { Text("按名称、旧标识或来源面板／分组搜索全部记录；空格分隔的关键词需同时匹配。") })
    if (query.isNotEmpty()) TextButton(onClick = { query = ""; limit = 25 }) { Text("清除未迁移搜索") }
    Text("匹配 ${matches.size} / ${entries.size} 项，已显示 ${minOf(limit, matches.size)} 项")
    if (matches.isEmpty()) Text("没有匹配的未迁移项目。请更换关键词或清除搜索。")
    matches.take(limit).forEach { item ->
        Text("${item.label.take(120)}（${item.target.take(120)}）")
        if (item.sourcePath.isNotEmpty()) Text("来源：${item.sourcePath.joinToString(" / ") { it.take(120) }}",
            style = MaterialTheme.typography.bodySmall)
    }
    if (limit < matches.size) TextButton(onClick = { limit = (limit + 25).coerceAtMost(matches.size) },
        modifier = Modifier.testTag("legacy-unmigrated-more")) { Text("再显示 ${minOf(25, matches.size - limit)} 项") }
}
