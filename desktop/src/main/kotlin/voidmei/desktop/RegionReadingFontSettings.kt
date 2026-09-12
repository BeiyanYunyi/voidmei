package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion

@Composable
internal fun RegionReadingFontSettings(region: HudRegion, update: (HudRegion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, Modifier.testTag("region-fonts-${region.id}")) {
        Text(if (expanded) "收起区域读数字体" else "区域读数字体（${if (region.readingLabelFont == null && region.readingNumberFont == null && region.readingTextSizes == null && region.readingTextWeights == null) "继承全局" else "自定义"}）")
    }
    if (!expanded) return
    Text(if (region.content == voidmei.config.HudRegionContent.ENGINE)
        "用于本区域表格。标签样式也用于发动机标题、控制条和增压器说明；留空继承默认样式。数字字体独立设置。"
        else "仅用于本区域表格的标签和数值（含单位）。留空继承全局文字字体或 HUD 数字字体。")
    RegionFontInput(region.readingLabelFont, "标签字体", "region-label-font-${region.id}", false) {
        update(region.copy(readingLabelFont = it))
    }
    RegionFontInput(region.readingNumberFont, "数字字体", "region-number-font-${region.id}", true) {
        update(region.copy(readingNumberFont = it))
    }
    RegionReadingWeightSettings(region) { update(region.copy(readingTextWeights = it)) }
    RegionReadingSizesSettings(region) { update(region.copy(readingTextSizes = it)) }
}

@Composable
private fun RegionFontInput(name: String?, label: String, tag: String, number: Boolean, onChange: (String?) -> Unit) {
    var draft by remember(name) { mutableStateOf(name.orEmpty()) }
    val next = draft.trim().takeUnless { it.isEmpty() }
    val valid = next == null || (next.length <= 200 && next.none { it.isISOControl() })
    val resolved = remember(next, number) { if (number) resolveHudNumberFont(next) else resolveTextFont(next) }
    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag(tag), singleLine = true,
        label = { Text(label) }, isError = !valid)
    if (!valid) Text("字体名称需为不超过 200 字的单行文本", color = MaterialTheme.colorScheme.error)
    if (resolved.unavailable) Text("未找到字体 $next，将使用${if (number) "默认等宽字体" else "系统默认字体"}。", color = MaterialTheme.colorScheme.error)
    TextButton(enabled = valid && next != name, onClick = { onChange(next) }, modifier = Modifier.testTag("$tag-apply")) { Text("应用$label") }
    if (next != null && valid) Text(if (number) "12345.6 km/h" else "速度 高度 发动机", fontFamily = resolved.family)
}
