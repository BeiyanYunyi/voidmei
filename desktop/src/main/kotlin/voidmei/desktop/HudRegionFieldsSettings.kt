package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.*
import voidmei.telemetry.*

@Composable
internal fun HudRegionFieldsSettings(region: HudRegion, settings: AppSettings, onChange: (HudRegion) -> Unit) {
    if (region.content != HudRegionContent.FLIGHT && region.content != HudRegionContent.ENGINE) return
    val inherited = if (region.content == HudRegionContent.FLIGHT) settings.hudFields else settings.hudEngineFields
    Row {
        Switch(region.fields != null, { onChange(region.copy(fields = if (it) inherited.toList() else null)) },
            Modifier.testTag("hud-region-fields-${region.id}"))
        Text("此区域独立选择字段")
    }
    val fields = region.fields ?: return
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起区域字段" else "编辑区域字段（${fields.size}）") }
    if (!expanded) return
    val choices = if (region.content == HudRegionContent.FLIGHT) HudField.entries.map { Triple(it.id, it.label, it.unit) }
        else HudEngineField.entries.map { Triple(it.id, it.label, it.unit) }
    fun save(ids: List<String>) = onChange(region.copy(fields = ids))
    fields.forEachIndexed { index, id ->
        FlowRow {
            Text(choices.firstOrNull { it.first == id }?.second ?: "未知字段 · $id")
            TextButton(onClick = { save(fields.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0,
                modifier = Modifier.testTag("hud-region-field-up-${region.id}-$index")) { Text("上移") }
            TextButton(onClick = { save(fields.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < fields.lastIndex,
                modifier = Modifier.testTag("hud-region-field-down-${region.id}-$index")) { Text("下移") }
            TextButton(onClick = { save(fields.filterIndexed { i, _ -> i != index }) },
                modifier = Modifier.testTag("hud-region-field-remove-${region.id}-$index")) { Text("移除") }
        }
    }
    if (fields.isEmpty()) Text("此区域未选择读数。")
    var query by remember { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, label = { Text("搜索可添加的区域字段") }, singleLine = true,
        modifier = Modifier.testTag("hud-region-field-search-${region.id}"))
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    FlowRow {
        choices.filter { choice -> choice.first !in fields && terms.all { "${choice.first} ${choice.second} ${choice.third}".contains(it, true) } }
            .forEach { choice -> TextButton(onClick = { save(fields + choice.first) },
                modifier = Modifier.testTag("hud-region-field-add-${region.id}-${choice.first}")) { Text("+ ${choice.second}") } }
    }
}
