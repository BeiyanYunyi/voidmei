package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import voidmei.telemetry.HudField

@Composable
internal fun HudFieldPicker(selected: List<HudField>, onAdd: (HudField) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, singleLine = true,
        label = { Text("搜索可添加的 HUD 字段") },
        supportingText = { Text("按名称、字段 ID 或单位查找；不改变已选字段。") },
        modifier = Modifier.fillMaxWidth().testTag("hud-field-search"))
    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除字段搜索") }
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val candidates = HudField.entries.filterNot { it in selected }.filter { field ->
        val text = "${field.label} ${field.id} ${field.unit}"
        terms.all { text.contains(it, ignoreCase = true) }
    }
    if (candidates.isEmpty()) Text(if (selected.size == HudField.entries.size) "已添加全部字段" else "没有匹配的可添加字段")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        candidates.forEach { field ->
            FilterChip(false, { onAdd(field) }, modifier = Modifier.testTag("hud-add-${field.id}"), label = { Text("+ ${field.label}") })
        }
    }
}
