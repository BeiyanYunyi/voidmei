package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.util.Locale

internal fun fontScalePercent(scale: Float): String = String.format(Locale.ROOT, "%.2f", scale * 100)
    .trimEnd('0').trimEnd('.')

@Composable
internal fun HudFontScaleInput(scale: Float?, inheritedScale: Float, tag: String, onChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("$tag-toggle")) {
        Text(if (expanded) "收起精确字号" else "精确字号（${fontScalePercent(scale ?: inheritedScale)}%${if (scale == null) "，继承" else ""}）")
    }
    if (!expanded) return
    var draft by remember(scale, scale ?: inheritedScale) { mutableStateOf(fontScalePercent(scale ?: inheritedScale)) }
    val parsed = draft.trim().takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?")) }
        ?.toFloatOrNull()?.takeIf { it.isFinite() && it in 75f..200f }?.div(100f)
    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag(tag), singleLine = true,
        label = { Text("文字缩放（75–200%）") }, isError = parsed == null,
        supportingText = { Text("最多两位小数。只调整文字，区域位置与尺寸另行设置。") })
    TextButton(enabled = parsed != null && parsed != scale, onClick = { parsed?.let(onChange) },
        modifier = Modifier.testTag("$tag-apply")) { Text("应用字号") }
}
