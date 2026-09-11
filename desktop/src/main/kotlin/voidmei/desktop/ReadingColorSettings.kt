package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import voidmei.config.AppSettings

@Composable
internal fun ReadingColorSettings(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(0) }
    TextButton(onClick = { expanded = !expanded }) { Text("表格默认配色") }
    if (expanded) Column {
        Text("用于主窗口和 HUD 的飞行、发动机表格；HUD 单独设置的颜色优先。留空使用内置颜色，阴影留空时关闭。")
        key(reset) {
            for ((key, label) in listOf("label" to "标签", "value" to "普通读数", "unit" to "单位", "warning" to "告警读数", "shade" to "文字阴影")) {
                ReadingColorInput("表格${label}颜色", settings.readingColors[key]) { color ->
                    onChange(settings.copy(readingColors = if (color == null) settings.readingColors - key else settings.readingColors + (key to color)))
                }
            }
        }
        TextButton(onClick = { reset++; onChange(settings.copy(readingColors = emptyMap())) }) { Text("恢复表格默认配色") }
    }
}
