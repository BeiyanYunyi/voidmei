package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import voidmei.config.HudRegion

@Composable
internal fun LegacyEngineFontSettings(fonts: Map<String, String>, regions: List<HudRegion>,
    targets: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    if (fonts.isEmpty()) return
    Text("迁移动力与控制标签字体")
    Text("面板 :font 优先，动力面板缺少该属性时使用历史 fontName 项。改变目标分区的表格、发动机标题及仪表说明标签字体，保留数字字体、字号、全局字体和其他区域。系统缺少该字体时使用默认字体回退。")
    if (regions.isEmpty()) Text("请先在上方新建发动机分区，或创建布局后重新预览。")
    fonts.forEach { (key, font) ->
        val name = if (key == "engineInfoSwitch") "动力信息" else "引擎控制"
        Text("$name：$font")
        LegacyEngineTargetPicker(regions, targets[key], "legacy-engine-font-$key", "不迁移${name}字体",
            occupiedIds = targets.filterKeys { it != key }.values.toSet(), enabled = true) { id ->
            onChange(if (id == null) targets - key else targets + (key to id))
        }
        regions.firstOrNull { it.id == targets[key] }?.let { region ->
            Text("$name → ${region.title.ifBlank { region.id }}：${region.readingLabelFont ?: "继承全局"} → $font（将应用）")
        }
    }
}
