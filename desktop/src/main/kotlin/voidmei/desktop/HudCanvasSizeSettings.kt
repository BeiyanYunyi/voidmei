package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudSceneLayout

@Composable
internal fun HudCanvasSizeSettings(scene: HudSceneLayout, onChange: (HudSceneLayout) -> Unit) {
    var width by remember(scene.width) { mutableStateOf(scene.width.toString()) }
    var height by remember(scene.height) { mutableStateOf(scene.height.toString()) }
    val validWidth = width.toIntOrNull()?.takeIf { it in 240..8192 }
    val validHeight = height.toIntOrNull()?.takeIf { it in 120..8192 }
    Text("画布尺寸（dp）")
    OutlinedTextField(width, { width = it }, label = { Text("画布宽度 · 240–8192 dp") }, singleLine = true,
        isError = validWidth == null, modifier = Modifier.testTag("hud-canvas-width"))
    OutlinedTextField(height, { height = it }, label = { Text("画布高度 · 120–8192 dp") }, singleLine = true,
        isError = validHeight == null, modifier = Modifier.testTag("hud-canvas-height"))
    Text("应用后才生效。缩小时将越界区域移回画布，必要时缩小区域；放大不改变已有区域的位置和尺寸。")
    Button(onClick = { if (validWidth != null && validHeight != null) onChange(scene.resizeCanvas(validWidth, validHeight)) },
        enabled = validWidth != null && validHeight != null && (validWidth != scene.width || validHeight != scene.height),
        modifier = Modifier.testTag("hud-canvas-apply")) { Text("应用画布尺寸") }
}
