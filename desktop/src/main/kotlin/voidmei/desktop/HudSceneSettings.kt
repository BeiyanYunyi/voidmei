package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.*
import kotlin.math.roundToInt

@Composable
internal fun HudSceneSettings(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val scene = settings.hudSceneLayout?.takeIf { it.enabled }
    TextButton(onClick = { onChange(settings.copy(hudSceneLayout = if (scene == null)
        settings.hudSceneLayout?.copy(enabled = true) ?: HudSceneLayout.initial(settings) else scene.copy(enabled = false))) },
        Modifier.testTag("hud-scene-toggle"), enabled = scene != null || com.sun.jna.Platform.isLinux() || com.sun.jna.Platform.isWindows()) {
        Text(if (scene == null) "使用单窗口分区布局（试验性）" else "返回纵向 HUD 布局")
    }
    if (scene == null) return
    Text("分区布局自动穿透鼠标。在此调整区域；预览同步显示。画布 ${scene.width} × ${scene.height} dp，空间不足时整体缩小。")
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起分区设置" else "调整分区位置与透明度") }
    if (!expanded) return
    scene.regions.forEach { region -> key(region.id) {
        fun update(value: HudRegion) = onChange(settings.copy(hudSceneLayout = scene.copy(
            regions = scene.regions.map { if (it.id == region.id) value else it })))
        Text(when (region.content) {
            HudRegionContent.FLIGHT -> "飞行读数"
            HudRegionContent.ENGINE -> "发动机 #${region.engineIndex}"
            HudRegionContent.ATTITUDE -> "姿态"
            HudRegionContent.MECHANIZATION -> "机械化"
            HudRegionContent.ALERTS -> "告警"
        })
        Text("位置 ${region.x}, ${region.y} dp")
        if (scene.width > region.width) Slider(region.x.toFloat(), { update(region.copy(x = it.roundToInt())) },
            valueRange = 0f..(scene.width - region.width).toFloat(), modifier = Modifier.testTag("hud-region-x-${region.id}"))
        if (scene.height > region.height) Slider(region.y.toFloat(), { update(region.copy(y = it.roundToInt())) },
            valueRange = 0f..(scene.height - region.height).toFloat(), modifier = Modifier.testTag("hud-region-y-${region.id}"))
        Text("尺寸 ${region.width} × ${region.height} dp")
        if (scene.width - region.x > 80) Slider(region.width.toFloat(), { update(region.copy(width = it.roundToInt())) },
            valueRange = 80f..(scene.width - region.x).toFloat(), modifier = Modifier.testTag("hud-region-width-${region.id}"))
        if (scene.height - region.y > 40) Slider(region.height.toFloat(), { update(region.copy(height = it.roundToInt())) },
            valueRange = 40f..(scene.height - region.y).toFloat(), modifier = Modifier.testTag("hud-region-height-${region.id}"))
        Text("背景不透明度 ${(region.backgroundAlpha * 100).roundToInt()}%")
        Slider(region.backgroundAlpha, { update(region.copy(backgroundAlpha = it)) },
            modifier = Modifier.testTag("hud-region-background-${region.id}"))
        Text("内容不透明度 ${(region.contentAlpha * 100).roundToInt()}%")
        Slider(region.contentAlpha, { update(region.copy(contentAlpha = it)) },
            modifier = Modifier.testTag("hud-region-content-${region.id}"))
    } }
}
