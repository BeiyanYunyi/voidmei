package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import voidmei.config.HudRegion

@Composable
internal fun LegacyEngineVisibilitySettings(visibility: Map<String, Boolean>, regions: List<HudRegion>,
    targets: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    if (visibility.isEmpty()) return
    Text("迁移动力与引擎控制显示开关")
    Text("分别选择对应发动机分区；同一分区只能接收一个旧窗口的开关。只修改分区可见状态，保留全局 HUD、字段和布局。")
    if (regions.isEmpty()) Text("请先创建发动机分区，再重新预览；可使用动力读数与引擎控制字段预设。")
    visibility.forEach { (key, visible) ->
        val name = if (key == "engineInfoSwitch") "动力信息" else "引擎控制"
        Text("$name：${if (visible) "显示" else "隐藏"}")
        LegacyEngineTargetPicker(regions, targets[key], "legacy-engine-visible-$key", "不迁移$name",
            occupiedIds = targets.filterKeys { it != key }.values.toSet(), enabled = true) { id ->
            onChange(if (id == null) targets - key else targets + (key to id))
        }
        regions.firstOrNull { it.id == targets[key] }?.let { region ->
            Text("$name → ${region.title.ifBlank { region.id }}：${if (region.visible) "显示" else "隐藏"} → ${if (visible) "显示" else "隐藏"}（将应用）")
        }
    }
}
