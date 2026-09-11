package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import voidmei.config.AppSettings
import voidmei.config.withHudLayout
import voidmei.telemetry.HudField
import kotlin.math.roundToInt

@Composable
internal fun HudSettingsPanel(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var previewRequest by remember { mutableStateOf(0) }
    var beforeReset by remember { mutableStateOf<AppSettings?>(null) }
    TextButton(onClick = { previewRequest++ }, modifier = Modifier.testTag("hud-layout-preview")) { Text("预览 HUD 布局") }
    if (previewRequest != 0) HudLayoutPreviewWindow(settings, previewRequest) { previewRequest = 0 }
    HudSceneSettings(settings, onChange)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(settings.hudAutoHideOnFocusLoss, { onChange(settings.copy(hudAutoHideOnFocusLoss = it)) },
            Modifier.testTag("hud-auto-hide-focus"),
            enabled = supportsGameFocus() || settings.hudAutoHideOnFocusLoss)
        Text("切出游戏时隐藏 HUD（Windows / Linux X11）")
    }
    if (!supportsGameFocus()) Text("当前会话无法检测游戏前台，HUD 将保持显示。", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(settings.hudClickThrough || settings.hudSceneLayout?.enabled == true, { onChange(settings.copy(hudClickThrough = it)) },
            Modifier.testTag("hud-click-through"),
            enabled = settings.hudSceneLayout?.enabled != true && System.getProperty("os.name", "").lowercase().let {
                it.contains("linux") || it.startsWith("windows")
            } || settings.hudSceneLayout?.enabled != true && settings.hudClickThrough)
        Text("HUD 鼠标穿透（Linux / Windows）")
    }
    Text("开启后鼠标操作下方窗口，HUD 无法拖动或点击关闭。可在此关闭穿透以重新调整 HUD。", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(settings.hudCompatibilityMode, { onChange(settings.copy(hudCompatibilityMode = it)) },
            Modifier.testTag("hud-compatibility"))
        Text("HUD 兼容显示（试验性）")
    }
    Text("透明 HUD 卡顿时可尝试开启。切换会重新打开 HUD 并保留位置；下次启动沿用此选择。", style = MaterialTheme.typography.bodySmall)
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起 HUD 字段设置" else "HUD 字段设置") }
    if (!expanded) return
    Text("高度读数来源")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        voidmei.telemetry.HudAltitudeMode.entries.forEach { mode ->
            FilterChip(settings.hudAltitudeMode == mode, { onChange(settings.copy(hudAltitudeMode = mode)) },
                label = { Text(mode.label) }, modifier = Modifier.testTag("hud-altitude-${mode.id}"))
        }
    }
    Text("低空模式在雷达估计高度 ≤500 m 时切换；雷达数据或单位推断不可用时显示海拔。", style = MaterialTheme.typography.bodySmall)
    Text("HUD 宽度 ${settings.hudWidthDp} dp")
    Slider(value = settings.hudWidthDp.toFloat(),
        onValueChange = { onChange(settings.copy(hudWidthDp = it.roundToInt())) },
        valueRange = 240f..1000f, steps = 37,
        modifier = Modifier.testTag("hud-width").semantics { contentDescription = "HUD 宽度" })
    Text("实际宽度受当前显示器工作区限制。", style = MaterialTheme.typography.bodySmall)
    Text("HUD 读数列数")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "自动", 1 to "单列", 2 to "双列").forEach { (columns, label) ->
            FilterChip(settings.hudReadingColumns == columns,
                { onChange(settings.copy(hudReadingColumns = columns)) },
                label = { Text(label) }, modifier = Modifier.testTag("hud-columns-$columns"))
        }
    }
    Text("自动根据文字宽度排列；固定列数按从左到右、从上到下显示。空间不足时标签与读数换行。", style = MaterialTheme.typography.bodySmall)
    Text("HUD 文字大小 ${(settings.hudFontScale * 100).roundToInt()}%")
    Slider(value = settings.hudFontScale,
        onValueChange = { onChange(settings.copy(hudFontScale = it)) },
        valueRange = 0.75f..2f, steps = 24,
        modifier = Modifier.testTag("hud-font-scale").semantics { contentDescription = "HUD 文字大小" })
    Text("在系统字体缩放基础上调整，仅影响 HUD。", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = settings.hudNumberFont.orEmpty(),
        onValueChange = { name ->
            if (name.length <= 200 && name.none { it.isISOControl() })
                onChange(settings.copy(hudNumberFont = name.takeUnless { it.isBlank() }))
        }, label = { Text("HUD 数字字体") }, singleLine = true,
        modifier = Modifier.testTag("hud-number-font"))
    Text("填写已安装的字体名称；留空继承全局数字字体。仅影响 HUD 表格读数。", style = MaterialTheme.typography.bodySmall)
    val numberFont = remember(settings.hudNumberFont, settings.numberFont) { resolveHudNumberFont(settings.hudNumberFont ?: settings.numberFont) }
    if (numberFont.unavailable) Text("未找到此字体，当前使用默认等宽字体；保留填写的名称。",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("hud-number-font-unavailable"))
    HudReadingColorSettings(settings, onChange)
    FilterChip(settings.hudEngineIndex != null,
        { onChange(settings.copy(hudEngineIndex = if (settings.hudEngineIndex == null) 1 else null)) },
        label = { Text("发动机读数") })
    settings.hudEngineIndex?.let { index ->
        HudEngineFieldSettings(settings.hudEngineFields) { onChange(settings.copy(hudEngineFields = it)) }
        var input by remember(index) { mutableStateOf(index.toString()) }
        val parsed = input.toIntOrNull()?.takeIf { it > 0 }
        OutlinedTextField(input, { text ->
            input = text
            text.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(settings.copy(hudEngineIndex = it)) }
        }, label = { Text("HUD 发动机编号") }, singleLine = true, isError = parsed == null,
            supportingText = { Text(if (parsed == null) "请输入正整数；仍显示上次有效编号。" else "按遥测编号选择，缺失时不改用其他发动机。") })
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(settings.hudAttitude, { onChange(settings.copy(hudAttitude = !settings.hudAttitude)) }, label = { Text("姿态图") })
        FilterChip(settings.hudMechanization, { onChange(settings.copy(hudMechanization = !settings.hudMechanization)) }, label = { Text("起落架/襟翼/减速板") })
        TextButton(onClick = {
            val defaults = settings.withHudLayout(AppSettings())
            if (defaults != settings) beforeReset = settings
            onChange(defaults)
        }) { Text("恢复默认") }
        beforeReset?.let { previous ->
            TextButton(onClick = {
                onChange(settings.withHudLayout(previous))
                beforeReset = null
            }) { Text("恢复重置前布局") }
        }
    }
    if (settings.hudMechanization) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(settings.hudGear, { onChange(settings.copy(hudGear = !settings.hudGear)) }, label = { Text("起落架") })
        FilterChip(settings.hudFlaps, { onChange(settings.copy(hudFlaps = !settings.hudFlaps)) }, label = { Text("襟翼/后掠及模型提示") })
        FilterChip(settings.hudFlapBar, { onChange(settings.copy(hudFlapBar = !settings.hudFlapBar)) }, label = { Text("襟翼开度条") })
        FilterChip(settings.hudAirbrake, { onChange(settings.copy(hudAirbrake = !settings.hudAirbrake)) }, label = { Text("减速板") })
    }
    Text("选中字段按下方顺序显示。估算值需要足够的有效遥测。", style = MaterialTheme.typography.bodySmall)
    val selected = HudField.selected(settings.hudFields)
    if (settings.hudAttitude) FilterChip(settings.hudAttitudeAoaLimits,
        { onChange(settings.copy(hudAttitudeAoaLimits = !settings.hudAttitudeAoaLimits)) },
        label = { Text("姿态图迎角极限线") })
    if (settings.hudAttitude) FilterChip(settings.hudAttitudeEarthFixed,
        { onChange(settings.copy(hudAttitudeEarthFixed = !settings.hudAttitudeEarthFixed)) },
        label = { Text(if (settings.hudAttitudeEarthFixed) "姿态：地面参考" else "姿态：机体参考") })
    FilterChip(settings.hudCrosshair, { onChange(settings.copy(hudCrosshair = !settings.hudCrosshair)) },
        label = { Text("准星") })
    if (settings.hudCrosshair) {
        FilterChip(settings.hudCrosshairRight, { onChange(settings.copy(hudCrosshairRight = !settings.hudCrosshairRight)) },
            label = { Text(if (settings.hudCrosshairRight) "位置：HUD 右侧" else "位置：HUD 中心") })
        Text(if (settings.hudCrosshairImage.isEmpty()) "样式：线框" else "图片：${settings.hudCrosshairImage}")
        Row {
            TextButton(onClick = { chooseCrosshairImage(settings.hudCrosshairImage)?.let { onChange(settings.copy(hudCrosshairImage = it, hudCrosshairStretch = false)) } }) { Text("选择图片") }
            TextButton(onClick = { onChange(settings.copy(hudCrosshairImage = "")) }) { Text("使用线框") }
        }
        if (settings.hudCrosshairImage.isNotEmpty()) FilterChip(settings.hudCrosshairStretch,
            { onChange(settings.copy(hudCrosshairStretch = !settings.hudCrosshairStretch)) },
            label = { Text(if (settings.hudCrosshairStretch) "图片：方形拉伸" else "图片：保持比例") })
        Text("准星尺寸 ${settings.hudCrosshairSizeDp} dp；移动 HUD 对准所需位置。")
        Slider(settings.hudCrosshairSizeDp.toFloat(), { onChange(settings.copy(hudCrosshairSizeDp = it.toInt())) }, valueRange = 24f..400f)
    }
    if (HudField.HEADING in selected) FilterChip(settings.hudCompassHeadingUp,
        { onChange(settings.copy(hudCompassHeadingUp = !settings.hudCompassHeadingUp)) },
        label = { Text(if (settings.hudCompassHeadingUp) "罗盘：航向朝上" else "罗盘：北向朝上") })
    if (HudField.AOA in selected) {
        AoaThresholdSetting("迎角数值预警阈值", "aoa-value-threshold", settings.hudAoaWarningPercent) {
            onChange(settings.copy(hudAoaWarningPercent = it))
        }
        AoaThresholdSetting("正迎角余量条预警阈值", "aoa-bar-threshold", settings.hudAoaBarWarningPercent) {
            onChange(settings.copy(hudAoaBarWarningPercent = it))
        }
        Text("剩余比例低于阈值时提示；达到模型上限始终提示。", style = MaterialTheme.typography.bodySmall)
    }

    if (HudField.FUEL_MASS_SHARE in selected) Text("燃油占基础质量加燃油的比例，不是机动性能；不计弹药、外挂和损伤。", style = MaterialTheme.typography.bodySmall)
    if (HudField.STALL_IAS in selected) Text("失速 IAS 为基础质量下的 1 G 模型估算，未计外挂载荷、损伤和机动过载。", style = MaterialTheme.typography.bodySmall)
    selected.forEachIndexed { index, field ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(true, { onChange(settings.copy(hudFields = settings.hudFields.filterNot { it == field.id })) },
                Modifier.testTag("hud-toggle-${field.id}").semantics { contentDescription = "显示${field.label}" })
            Text(field.label, Modifier.weight(1f))
            FilterChip(field.id !in settings.hudHiddenLabels, {
                onChange(settings.copy(hudHiddenLabels = if (field.id in settings.hudHiddenLabels)
                    settings.hudHiddenLabels - field.id else settings.hudHiddenLabels + field.id))
            }, label = { Text("标签") }, modifier = Modifier.testTag("hud-label-${field.id}"))
            fun move(otherIndex: Int) {
                val ids = settings.hudFields.toMutableList()
                val a = ids.indexOf(field.id)
                val b = ids.indexOf(selected[otherIndex].id)
                val previous = ids[a]; ids[a] = ids[b]; ids[b] = previous
                onChange(settings.copy(hudFields = ids))
            }
            TextButton(modifier = Modifier.testTag("hud-up-${field.id}"), enabled = index > 0, onClick = { move(index - 1) }) { Text("上移") }
            TextButton(modifier = Modifier.testTag("hud-down-${field.id}"), enabled = index < selected.lastIndex, onClick = { move(index + 1) }) { Text("下移") }
        }
    }
    HudFieldPicker(selected) { field -> onChange(settings.copy(hudFields = settings.hudFields + field.id)) }
}

@Composable
internal fun AoaThresholdSetting(label: String, tag: String, value: Double, onChange: (Double) -> Unit) {
    var input by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        // Preserve the user's spelling when this update acknowledges their own edit.
        if (input.toDoubleOrNull() != value) input = value.toString()
    }
    val parsed = input.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
    OutlinedTextField(input, { text ->
        if (text.length <= 64) {
            input = text
            text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let(onChange)
        }
    }, label = { Text("$label（%）") }, singleLine = true, isError = parsed == null,
        modifier = Modifier.testTag("$tag-input"),
        supportingText = { Text(if (parsed == null) "请输入 0–100 的有限数值；仍使用上次有效值。" else "支持小数；滑块按整数调整。") })
    Slider(value.toFloat(), {
        val selected = it.roundToInt().toDouble()
        input = selected.toString()
        onChange(selected)
    }, valueRange = 0f..100f, steps = 99,
        modifier = Modifier.testTag("$tag-slider").semantics { contentDescription = label })
}
