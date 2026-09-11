package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import voidmei.telemetry.HudEngineField

@Composable
internal fun HudEngineFieldSettings(ids: List<String>, onChange: (List<String>) -> Unit) {
    Text("发动机显示字段")
    val selected = HudEngineField.selected(ids)
    selected.forEachIndexed { index, field ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(true, { onChange(ids - field.id) }, Modifier.testTag("hud-engine-field-${field.id}")
                .semantics { contentDescription = "显示发动机${field.label}" })
            Text(field.label, Modifier.weight(1f))
            fun move(destination: Int) {
                val updated = ids.toMutableList()
                val from = updated.indexOf(field.id)
                val to = updated.indexOf(selected[destination].id)
                updated[from] = updated[to]
                updated[to] = field.id
                onChange(updated)
            }
            TextButton(enabled = index > 0, onClick = { move(index - 1) },
                modifier = Modifier.testTag("hud-engine-up-${field.id}")) { Text("上移") }
            TextButton(enabled = index < selected.lastIndex, onClick = { move(index + 1) },
                modifier = Modifier.testTag("hud-engine-down-${field.id}")) { Text("下移") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HudEngineField.entries.filterNot { it in selected }.forEach { field ->
            FilterChip(false, { onChange(ids + field.id) }, label = { Text("+ ${field.label}") },
                modifier = Modifier.testTag("hud-engine-field-${field.id}"))
        }
    }
    Text("上移/下移调整显示顺序；重新加入的字段排在末尾。", style = MaterialTheme.typography.bodySmall)
}
