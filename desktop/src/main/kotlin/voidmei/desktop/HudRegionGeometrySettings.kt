package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion
import voidmei.config.HudSceneLayout

@Composable
internal fun HudRegionGeometrySettings(region: HudRegion, scene: HudSceneLayout, onChange: (HudRegion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("hud-region-geometry-${region.id}")) {
        Text(if (expanded) "收起精确位置与尺寸" else "输入精确位置与尺寸")
    }
    if (!expanded) return
    var x by remember(region.x) { mutableStateOf(region.x.toString()) }
    var y by remember(region.y) { mutableStateOf(region.y.toString()) }
    var width by remember(region.width) { mutableStateOf(region.width.toString()) }
    var height by remember(region.height) { mutableStateOf(region.height.toString()) }
    val px = x.toIntOrNull()?.takeIf { it in 0..8192 }
    val py = y.toIntOrNull()?.takeIf { it in 0..8192 }
    val w = width.toIntOrNull()?.takeIf { it in 80..8192 }
    val h = height.toIntOrNull()?.takeIf { it in 40..8192 }
    val fits = px != null && py != null && w != null && h != null && px + w <= scene.width && py + h <= scene.height
    OutlinedTextField(x, { x = it }, label = { Text("左侧位置 X（dp）") }, singleLine = true,
        isError = px == null, modifier = Modifier.testTag("hud-region-input-x-${region.id}"))
    OutlinedTextField(y, { y = it }, label = { Text("顶部位置 Y（dp）") }, singleLine = true,
        isError = py == null, modifier = Modifier.testTag("hud-region-input-y-${region.id}"))
    OutlinedTextField(width, { width = it }, label = { Text("区域宽度（至少 80 dp）") }, singleLine = true,
        isError = w == null, modifier = Modifier.testTag("hud-region-input-width-${region.id}"))
    OutlinedTextField(height, { height = it }, label = { Text("区域高度（至少 40 dp）") }, singleLine = true,
        isError = h == null, modifier = Modifier.testTag("hud-region-input-height-${region.id}"))
    Text(if (fits) "应用时同时更新位置和尺寸。" else "请输入整数，区域须完整位于 ${scene.width} × ${scene.height} dp 画布内。")
    Button(onClick = {
        if (fits) onChange(region.copy(x = px!!, y = py!!, width = w!!, height = h!!))
    }, enabled = fits && (px != region.x || py != region.y || w != region.width || h != region.height),
        modifier = Modifier.testTag("hud-region-geometry-apply-${region.id}")) { Text("应用位置与尺寸") }
}
