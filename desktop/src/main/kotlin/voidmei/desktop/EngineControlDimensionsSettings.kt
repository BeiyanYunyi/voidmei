package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.*

@Composable
internal fun EngineControlDimensionsSettings(region: HudRegion, update: (HudRegion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton({ expanded = !expanded }, Modifier.testTag("engine-dimensions-${region.id}")) { Text(if (expanded) "收起控制条尺寸" else "控制条尺寸") }
    if (!expanded) return
    var length by remember(region.id, region.engineControlDimensions) { mutableStateOf((region.engineControlDimensions?.lengthDp ?: 112).toString()) }
    var thickness by remember(region.id, region.engineControlDimensions) { mutableStateOf((region.engineControlDimensions?.thicknessDp ?: 18).toString()) }
    val l = length.toIntOrNull()?.takeIf { it in 48..512 }
    val t = thickness.toIntOrNull()?.takeIf { it in 8..48 }
    Text("此分区所有发动机控制条及增压器刻度共用尺寸。横条长度受可用宽度限制；竖条长度对应高度。字体与区域尺寸独立。")
    OutlinedTextField(length, { length = it }, singleLine = true, label = { Text("条长（48–512 dp）") }, isError = l == null,
        modifier = Modifier.fillMaxWidth().testTag("engine-length-${region.id}"))
    OutlinedTextField(thickness, { thickness = it }, singleLine = true, label = { Text("厚度（8–48 dp）") }, isError = t == null,
        modifier = Modifier.fillMaxWidth().testTag("engine-thickness-${region.id}"))
    val next = if (l != null && t != null) EngineControlDimensions(l, t) else null
    TextButton({ update(region.copy(engineControlDimensions = next)) }, enabled = next != null && next != region.engineControlDimensions,
        modifier = Modifier.testTag("engine-dimensions-apply-${region.id}")) { Text("应用控制条尺寸") }
    TextButton({ update(region.copy(engineControlDimensions = null)) }, enabled = region.engineControlDimensions != null,
        modifier = Modifier.testTag("engine-dimensions-reset-${region.id}")) { Text("恢复默认自适应尺寸") }
}
