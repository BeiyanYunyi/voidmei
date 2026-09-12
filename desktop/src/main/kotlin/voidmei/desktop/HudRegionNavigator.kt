package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import voidmei.config.HudRegion
import voidmei.config.HudRegionContent

/** Search only the directory; editors stay composed so incomplete input is retained. */
@Composable
internal fun HudRegionNavigator(regions: List<HudRegion>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    TextButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.testTag("hud-region-directory-toggle")) {
        Text(if (expanded) "收起区域目录" else "查找并跳转到区域（${regions.size}）")
    }
    if (!expanded) return
    OutlinedTextField(query, { query = it }, singleLine = true,
        label = { Text("搜索区域类型、标题或编号") }, modifier = Modifier.fillMaxWidth().testTag("hud-region-search"),
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } })
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matches = regions.filter { region ->
        val text = listOf(region.content.label, region.content.name, region.id, region.title,
            if (region.content == HudRegionContent.ENGINE) "#${region.engineIndex} ${region.engineIndex} 号发动机" else "").joinToString(" ")
        terms.all { text.contains(it, ignoreCase = true) }
    }
    Text("找到 ${matches.size} / ${regions.size} 个区域；按图层从底到顶排列。", style = MaterialTheme.typography.bodySmall)
    if (matches.isEmpty()) Text("没有匹配的区域，请更换关键词或清除搜索。")
    else Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        matches.forEach { region ->
            TextButton(onClick = { onNavigate(region.id) },
                modifier = Modifier.fillMaxWidth().testTag("hud-region-jump-${region.id}")) {
                Column(Modifier.fillMaxWidth()) {
                    Text(region.title.ifBlank { region.content.label } +
                        if (region.content == HudRegionContent.ENGINE) " · ${region.engineIndex} 号发动机" else "")
                    Text("${region.content.label} · ${region.id} · ${if (region.visible) "显示" else "已隐藏"}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
