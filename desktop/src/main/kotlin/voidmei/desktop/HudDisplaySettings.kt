package voidmei.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudSceneLayout

@Composable
internal fun HudDisplaySettings(scene: HudSceneLayout, onChange: (HudSceneLayout) -> Unit) {
    var displays by remember { mutableStateOf(hudDisplays()) }
    Text("分区窗口覆盖范围")
    FlowRow {
        FilterChip(scene.displayId == null, { onChange(scene.copy(displayId = null)) }, label = { Text("按画布尺寸") },
            modifier = Modifier.testTag("hud-display-canvas"))
        displays.forEachIndexed { index, display ->
            FilterChip(scene.displayId == display.id, { onChange(scene.copy(displayId = display.id)) },
                label = { Text("显示器 ${index + 1}${if (display.primary) "（主屏）" else ""}") },
                modifier = Modifier.testTag("hud-display-$index"))
        }
    }
    TextButton(onClick = { displays = hudDisplays() }) { Text("刷新显示器列表") }
    if (scene.displayId != null) {
        Text("覆盖所选显示器（含任务栏区域），按比例适配画布；宽高比不同时留出透明空白。")
        if (displays.none { it.id == scene.displayId }) Text("所选显示器不可用，暂时使用主屏；重新接入后自动恢复。")
    }
}
