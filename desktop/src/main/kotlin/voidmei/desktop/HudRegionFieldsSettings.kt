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
    if (region.content == HudRegionContent.CONTROLS) {
        val axes = listOf(HudField.AILERON, HudField.ELEVATOR, HudField.RUDDER, HudField.WING_SWEEP)
        val selected = region.fields ?: axes.take(3).map { it.id }
        Text("显示的操纵面")
        FlowRow {
            axes.forEach { field ->
                FilterChip(field.id in selected, { onChange(region.copy(fields =
                    if (field.id in selected) selected.filterNot { it == field.id } else selected + field.id)) },
                    label = { Text(field.label) }, modifier = Modifier.testTag("hud-region-control-${region.id}-${field.id}"))
            }
        }
        var ordering by remember { mutableStateOf(false) }
        TextButton({ ordering = !ordering }, Modifier.testTag("hud-region-control-order-${region.id}")) {
            Text(if (ordering) "收起操纵面顺序" else "调整操纵面顺序")
        }
        if (ordering) {
            val visible = selected.distinct().filter { id -> axes.any { it.id == id } }
            fun move(id: String, other: String) {
                onChange(region.copy(fields = selected.map { if (it == id) other else if (it == other) id else it }))
            }
            visible.forEachIndexed { index, id ->
                FlowRow {
                    Text(axes.first { it.id == id }.label)
                    TextButton({ move(id, visible[index - 1]) }, enabled = index > 0,
                        modifier = Modifier.testTag("hud-region-control-up-${region.id}-$id")) { Text("上移") }
                    TextButton({ move(id, visible[index + 1]) }, enabled = index < visible.lastIndex,
                        modifier = Modifier.testTag("hud-region-control-down-${region.id}-$id")) { Text("下移") }
                }
            }
        }
        return
    }
    if (region.content == HudRegionContent.ALERTS) {
        val selected = region.fields ?: AlertSeverity.entries.map { it.name.lowercase() }
        Text("告警类别（无匹配告警时隐藏区域）")
        FlowRow {
            AlertSeverity.entries.forEach { severity ->
                val id = severity.name.lowercase()
                FilterChip(id in selected, { onChange(region.copy(fields =
                    if (id in selected) selected.filterNot { it == id } else selected + id)) },
                    label = { Text(if (severity == AlertSeverity.WARNING) "警告" else "提示") },
                    modifier = Modifier.testTag("hud-region-alert-${region.id}-$id"))
            }
        }
        return
    }
    if (region.content == HudRegionContent.MESSAGES) {
        val selected = region.fields ?: HudMessageKind.entries.map { it.name.lowercase() }
        Text("消息类别（筛选后显示最近 ${region.messageLimit} 条）")
        Text("单条消息最大行数（超出显示省略号）")
        FlowRow {
            listOf(0, 1, 2, 3, 5, 10).forEach { lines ->
                FilterChip(region.messageMaxLines == lines, { onChange(region.copy(messageMaxLines = lines)) },
                    label = { Text(if (lines == 0) "完整显示" else "$lines 行") },
                    modifier = Modifier.testTag("hud-region-message-lines-${region.id}-$lines"))
            }
        }
        Text("完整原文可在“查看游戏消息”中阅读。")
        FlowRow {
            listOf(1, 3, 5, 10, 20).forEach { limit ->
                FilterChip(region.messageLimit == limit, { onChange(region.copy(messageLimit = limit)) },
                    label = { Text("$limit 条") }, modifier = Modifier.testTag("hud-region-message-limit-${region.id}-$limit"))
            }
        }
        FlowRow {
            HudMessageKind.entries.forEach { kind ->
                val id = kind.name.lowercase()
                FilterChip(id in selected, { onChange(region.copy(fields =
                    if (id in selected) selected.filterNot { it == id } else selected + id)) },
                    label = { Text(if (kind == HudMessageKind.EVENT) "事件" else "损伤") },
                    modifier = Modifier.testTag("hud-region-message-${region.id}-$id"))
            }
        }
        return
    }
    if (region.content == HudRegionContent.MECHANIZATION) {
        Row {
            Switch(region.fields != null, { onChange(region.copy(fields = if (it) HudMechanizationField.inherited(settings) else null)) },
                Modifier.testTag("hud-region-fields-${region.id}"))
            Text("此区域独立选择机械化内容")
        }
        region.fields?.let { fields ->
            FlowRow {
                HudMechanizationField.entries.forEach { field ->
                    FilterChip(field.id in fields, { onChange(region.copy(fields =
                        if (field.id in fields) fields.filterNot { it == field.id } else fields + field.id)) },
                        label = { Text(field.label) }, modifier = Modifier.testTag("hud-region-mechanization-${region.id}-${field.id}"))
                }
            }
            if (HudMechanizationField.entries.none { it.id in fields }) Text("此区域未选择机械化内容。")
        }
        return
    }
    if (region.content != HudRegionContent.FLIGHT && region.content != HudRegionContent.ENGINE) return
    val inherited = if (region.content == HudRegionContent.FLIGHT) settings.hudFields else settings.hudEngineFields
    Row {
        Switch(region.hiddenLabels != null, { onChange(region.copy(hiddenLabels = if (it)
            (if (region.content == HudRegionContent.FLIGHT) settings.hudHiddenLabels.toList() else emptyList()) else null)) },
            Modifier.testTag("hud-region-labels-${region.id}"))
        Text("此区域独立选择标签")
    }
    region.hiddenLabels?.let { hidden ->
        val labels = if (region.content == HudRegionContent.FLIGHT) HudField.entries.map { it.id to it.label }
            else HudEngineField.entries.map { it.id to it.label }
        Text("选中表示显示标签；关闭后保留数值和单位。")
        FlowRow {
            (region.fields ?: inherited).distinct().forEach { id ->
                labels.firstOrNull { it.first == id }?.let { (_, label) ->
                    FilterChip(id !in hidden, { onChange(region.copy(hiddenLabels =
                        if (id in hidden) hidden - id else hidden + id)) }, label = { Text(label) },
                        modifier = Modifier.testTag("hud-region-label-${region.id}-$id"))
                }
            }
        }
    }
    Row {
        Switch(region.fields != null, { onChange(region.copy(fields = if (it) inherited.toList() else null)) },
            Modifier.testTag("hud-region-fields-${region.id}"))
        Text("此区域独立选择字段")
    }
    if (region.content == HudRegionContent.ENGINE) {
        Text("应用预设将为此区域启用独立字段；可添加两个发动机区域，分别选择动力读数与引擎控制。")
        HudEngineFieldPresets("hud-region-${region.id}") { onChange(region.copy(fields = it)) }
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
