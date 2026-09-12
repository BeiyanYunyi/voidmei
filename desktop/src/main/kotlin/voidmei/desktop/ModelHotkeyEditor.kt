package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.*

@Composable
internal fun ModelHotkeyEditor(settings: AppSettings, enabled: Boolean, onChange: (AppSettings) -> Unit) {
    val current = ModelHotkey.parse(settings.modelWindowHotkey)
    var expanded by remember { mutableStateOf(false) }
    var error by remember(settings.modelWindowHotkey) { mutableStateOf<String?>(null) }
    fun update(binding: ModelHotkey) {
        if (binding.conflictsWithHud) error = "Ctrl+Shift+H 已用于 HUD；请选其他组合。"
        else { error = null; onChange(settings.copy(modelWindowHotkey = binding.encoded)) }
    }
    FlowRow {
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.testTag("model-hotkey-key")) { Text("按键：${current.key.name}") }
            DropdownMenu(expanded, { expanded = false }) {
                ModelHotkeyKey.entries.forEach { key -> DropdownMenuItem(text = { Text(key.name) },
                    onClick = { expanded = false; update(current.copy(key = key)) }, modifier = Modifier.testTag("model-hotkey-key-${key.name}")) }
            }
        }
        FilterChip(current.ctrl, { update(current.copy(ctrl = !current.ctrl)) }, enabled = enabled, label = { Text("Ctrl") }, modifier = Modifier.testTag("model-hotkey-ctrl"))
        FilterChip(current.shift, { update(current.copy(shift = !current.shift)) }, enabled = enabled, label = { Text("Shift") }, modifier = Modifier.testTag("model-hotkey-shift"))
        FilterChip(current.alt, { update(current.copy(alt = !current.alt)) }, enabled = enabled, label = { Text("Alt") }, modifier = Modifier.testTag("model-hotkey-alt"))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("选择 A–Z 或 F1–F12，可不加修饰键；单键也会在其他应用输入时触发。修改后立即使用新组合。", style = MaterialTheme.typography.bodySmall)
}
