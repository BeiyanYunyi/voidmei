package voidmei.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion

@Composable
internal fun HudDragTargetSettings(regions: List<HudRegion>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun HudRegion.label() = "${title.ifBlank { content.label }} · $id${if (visible) "" else " · 已隐藏"}"
    Box {
        TextButton({ expanded = true }, Modifier.testTag("hud-drag-target-menu")) {
            Text("拖动目标：${regions.firstOrNull { it.id == selected }?.label() ?: "自动选取顶层"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("自动选取顶层") }, onClick = { onSelect(null); expanded = false },
                modifier = Modifier.testTag("hud-drag-target-auto"))
            regions.forEach { region ->
                DropdownMenuItem(text = { Text(region.label()) }, onClick = { onSelect(region.id); expanded = false },
                    modifier = Modifier.testTag("hud-drag-target-${region.id}"))
            }
        }
    }
}
