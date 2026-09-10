package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import voidmei.config.AppSettings
import voidmei.config.parseHexColor

@Composable
internal fun HudReadingColorSettings(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var resetVersion by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("HUD 读数颜色") }
    if (expanded) Column {
        Text("用于 HUD 飞行与发动机表格读数。输入 #RRGGBB 或 #RRGGBBAA（末两位为透明度），留空使用默认颜色。")
        key(resetVersion) {
        ReadingColorInput("标签颜色", settings.hudLabelColor) { onChange(settings.copy(hudLabelColor = it)) }
        ReadingColorInput("普通读数颜色", settings.hudValueColor) { onChange(settings.copy(hudValueColor = it)) }
        ReadingColorInput("单位颜色", settings.hudUnitColor) { onChange(settings.copy(hudUnitColor = it)) }
        ReadingColorInput("文字阴影颜色", settings.hudShadeColor) { onChange(settings.copy(hudShadeColor = it)) }
        Text("阴影颜色留空时关闭；偏移随 HUD 字体缩放。")
        ReadingColorInput("告警读数颜色", settings.hudWarningColor) { onChange(settings.copy(hudWarningColor = it)) }
        }
        TextButton(onClick = { resetVersion++; onChange(settings.copy(hudLabelColor = null, hudValueColor = null, hudWarningColor = null, hudUnitColor = null, hudShadeColor = null)) }) { Text("恢复读数默认颜色") }
    }
}

@Composable
private fun ReadingColorInput(label: String, value: String?, onChange: (String?) -> Unit) {
    var input by remember(value) { mutableStateOf(value.orEmpty()) }
    val valid = input.isEmpty() || parseHexColor(input) != null
    OutlinedTextField(input, { text ->
        input = text
        if (text.isEmpty() || parseHexColor(text) != null) onChange(text.takeIf { it.isNotEmpty() })
    }, label = { Text(label) }, singleLine = true, isError = !valid,
        supportingText = { if (!valid) Text("请输入六位 RGB 或八位 RGBA 十六进制颜色；仍使用上次有效颜色。") })
}
