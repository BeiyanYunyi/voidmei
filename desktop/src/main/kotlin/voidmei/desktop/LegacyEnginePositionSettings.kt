package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import voidmei.config.*

@Composable
internal fun LegacyEnginePositionSettings(positions: Map<String, LegacyHudPosition>, scene: HudSceneLayout?,
    screen: LegacyScreenSize?, targets: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    if (positions.isEmpty()) return
    Text("迁移动力与引擎控制位置")
    Text("选择不同的发动机分区接收旧窗口位置；保留分区尺寸、字段和显示开关。坐标按当前画布换算并限制在画布内。")
    val regions = scene?.regions.orEmpty().filter { it.content == HudRegionContent.ENGINE }
    if (regions.isEmpty()) Text("请先创建发动机分区，再重新预览。")
    positions.forEach { (key, position) ->
        val name = if (key == "engineInfoSwitch") "动力信息" else "引擎控制"
        val ready = !position.needsScreenSize || screen != null
        Text("$name 旧坐标：(${position.x}, ${position.y})")
        if (!ready) Text("$name：需要填写旧屏幕宽高，暂不应用位置。")
        LegacyEngineTargetPicker(regions, targets[key], "legacy-engine-position-$key", "不迁移${name}位置",
            occupiedIds = targets.filterKeys { it != key }.values.toSet(), enabled = ready) { id ->
            onChange(if (id == null) targets - key else targets + (key to id))
        }
        if (ready) regions.firstOrNull { it.id == targets[key] }?.let { region ->
            val moved = region.withLegacyPosition(position, scene!!.width, scene.height, screen)
            Text("$name → ${region.title.ifBlank { region.id }}：(${region.x}, ${region.y}) → (${moved.x}, ${moved.y}) dp（将应用）")
        }
    }
}
