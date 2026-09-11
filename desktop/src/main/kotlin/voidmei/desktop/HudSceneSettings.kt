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
    var removed by remember { mutableStateOf<Pair<HudRegion, Int>?>(null) }
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
    removed?.let { (region, layer) ->
        TextButton(onClick = {
            onChange(settings.copy(hudSceneLayout = scene.restoreRegion(region, layer)))
            removed = null
        }, enabled = scene.regions.size < 32, modifier = Modifier.testTag("hud-region-restore")) {
            Text("恢复上次移除的区域 · ${region.content.label}")
        }
        Text(if (scene.regions.size >= 32) "已达 32 个区域，暂不能恢复；再次移除会替换这条恢复记录。"
            else "仅保留本次设置页面中的最近一次移除；恢复时适配当前画布，编号冲突时使用新编号。")
    }
    if (!expanded) return
    HudDisplaySettings(scene) { onChange(settings.copy(hudSceneLayout = it)) }
    HudCanvasSizeSettings(scene) { onChange(settings.copy(hudSceneLayout = it)) }
    Text("添加区域（${scene.regions.size}/32）")
    FlowRow {
        HudRegionContent.entries.forEach { content ->
            TextButton(onClick = { onChange(settings.copy(hudSceneLayout = scene.addRegion(content))) },
                enabled = scene.regions.size < 32, modifier = Modifier.testTag("hud-region-add-${content.name}")) {
                Text("+ ${content.label}")
            }
        }
    }
    Text("同类区域可重复添加；读数字段默认沿用 HUD 设置，也可独立选择。至少保留一个区域。")
    Text("区域列表从底层到顶层排列；重叠时，顶层区域会覆盖下层。")
    scene.regions.forEachIndexed { layer, region -> key(region.id) {
        fun update(value: HudRegion) = onChange(settings.copy(hudSceneLayout = scene.copy(
            regions = scene.regions.map { if (it.id == region.id) value else it })))
        Text("${region.content.label}${if (region.content == HudRegionContent.ENGINE) " #${region.engineIndex}" else ""} · ${region.id}")
        OutlinedTextField(region.title, { value -> update(region.copy(title = value.filterNot { it.isISOControl() }.take(80))) },
            label = { Text("区域标题（可选）") }, supportingText = { Text("最多 80 字符；留空不显示额外标题。") },
            singleLine = true, modifier = Modifier.testTag("hud-region-title-${region.id}"))
        if (region.content == HudRegionContent.CROSSHAIR) Text("准星居中显示，大小随区域尺寸调整，样式沿用准星图片设置。存在准星区域时替代整窗准星，由区域显示开关控制。")
        Row {
            Switch(region.visible, { update(region.copy(visible = it)) }, Modifier.testTag("hud-region-visible-${region.id}"))
            Text("显示此区域")
        }
        Row {
            TextButton(onClick = { onChange(settings.copy(hudSceneLayout = scene.moveRegionLayer(region.id, false))) },
                enabled = layer > 0, modifier = Modifier.testTag("hud-region-layer-down-${region.id}")) { Text("下移一层") }
            TextButton(onClick = { onChange(settings.copy(hudSceneLayout = scene.moveRegionLayer(region.id, true))) },
                enabled = layer < scene.regions.lastIndex, modifier = Modifier.testTag("hud-region-layer-up-${region.id}")) { Text("上移一层") }
        }
        TextButton(onClick = { onChange(settings.copy(hudSceneLayout = scene.duplicateRegion(region.id))) },
            enabled = scene.regions.size < 32, modifier = Modifier.testTag("hud-region-duplicate-${region.id}")) { Text("复制此区域") }
        TextButton(onClick = {
            removed = region to layer
            onChange(settings.copy(hudSceneLayout = scene.removeRegion(region.id)))
        },
            enabled = scene.regions.size > 1, modifier = Modifier.testTag("hud-region-remove-${region.id}")) { Text("移除此区域") }
        HudRegionFieldsSettings(region, settings, ::update)
        if (region.content == HudRegionContent.FLIGHT || region.content == HudRegionContent.ENGINE) {
            Text("区域读数列数")
            FlowRow {
                listOf(null to "继承全局", 0 to "自动", 1 to "单列", 2 to "双列").forEach { (columns, label) ->
                    FilterChip(region.readingColumns == columns, { update(region.copy(readingColumns = columns)) },
                        label = { Text(label) }, modifier = Modifier.testTag("hud-region-columns-${region.id}-${columns ?: "inherit"}"))
                }
            }
        }
        if (region.content == HudRegionContent.ENGINE) {
            var engineText by remember(region.engineIndex) { mutableStateOf(region.engineIndex.toString()) }
            val validEngine = engineText.toIntOrNull()?.takeIf { it > 0 }
            OutlinedTextField(engineText, { value ->
                engineText = value
                value.toIntOrNull()?.takeIf { it > 0 }?.let { update(region.copy(engineIndex = it)) }
            }, label = { Text("发动机编号") }, singleLine = true, isError = validEngine == null,
                supportingText = { Text(if (validEngine == null) "请输入正整数；暂未应用此输入。" else "缺少此编号的数据时显示未知，不替换为其他发动机。") },
                modifier = Modifier.testTag("hud-region-engine-${region.id}"))
        }
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
