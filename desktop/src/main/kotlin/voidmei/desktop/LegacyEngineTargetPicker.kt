package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion

/** Keep the active destination visible even when filtering or paging other candidates. */
@Composable
internal fun LegacyEngineTargetPicker(regions: List<HudRegion>, selectedId: String?, tag: String,
    noneLabel: String, occupiedIds: Set<String> = emptySet(), enabled: Boolean = true,
    onChange: (String?) -> Unit) {
    var query by remember(tag) { mutableStateOf("") }
    var limit by remember(tag, query) { mutableStateOf(6) }
    fun label(region: HudRegion) = listOf(region.title.takeIf { it.isNotBlank() }, region.id,
        "发动机 #${region.engineIndex}").filterNotNull().joinToString(" · ")
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matches = regions.filter { region -> terms.all { label(region).contains(it, ignoreCase = true) } }
    val selected = regions.firstOrNull { it.id == selectedId }
    if (regions.size > 6 || query.isNotEmpty()) {
        OutlinedTextField(query, { query = it }, singleLine = true,
            label = { Text("搜索目标分区") }, supportingText = { Text("按名称、ID 或发动机编号（如 #2）搜索；保留当前选择。") },
            modifier = Modifier.fillMaxWidth().testTag("$tag-search"))
        if (query.isNotEmpty()) TextButton({ query = "" }, Modifier.testTag("$tag-search-clear")) { Text("清除目标搜索") }
        Text("匹配 ${matches.size} / ${regions.size} 个分区", style = MaterialTheme.typography.bodySmall)
    }
    FlowRow {
        FilterChip(selected == null, { onChange(null) }, label = { Text(noneLabel) },
            modifier = Modifier.testTag("$tag-none"))
        val visible = (listOfNotNull(selected) + matches.take(limit)).distinctBy { it.id }
        visible.forEach { region ->
            val occupied = region.id in occupiedIds
            FilterChip(region.id == selectedId, { onChange(region.id) }, enabled = enabled && !occupied,
                label = { Text(label(region) + if (occupied) "（已选给其他窗口）" else "") },
                modifier = Modifier.testTag("$tag-${region.id}"))
        }
    }
    if (matches.isEmpty() && query.isNotEmpty()) Text("没有匹配的目标分区；当前选择仍保留。")
    if (matches.size > limit) TextButton({ limit += 6 }, Modifier.testTag("$tag-more")) { Text("再显示 ${minOf(6, matches.size - limit)} 个分区") }
}
