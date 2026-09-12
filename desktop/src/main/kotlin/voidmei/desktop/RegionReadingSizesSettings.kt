package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion
import voidmei.config.ReadingTextSizes

@Composable
internal fun RegionReadingSizesSettings(region: HudRegion, onChange: (ReadingTextSizes?) -> Unit) {
    val current = region.readingTextSizes ?: ReadingTextSizes()
    var label by remember(region.readingTextSizes) { mutableStateOf(current.label.toString()) }
    var number by remember(region.readingTextSizes) { mutableStateOf(current.number.toString()) }
    var unit by remember(region.readingTextSizes) { mutableStateOf(current.unit.toString()) }
    fun parse(value: String) = value.toFloatOrNull()?.takeIf { it.isFinite() && it in 6f..64f }
    val valid = listOf(label, number, unit).all { parse(it) != null }
    Text("表格基础字号（6–64 sp）")
    Text("标签、数字与单位可分别调整；仍受区域文字缩放和系统字体缩放影响。区域尺寸另行调整。", style = MaterialTheme.typography.bodySmall)
    listOf(Triple("标签", label, { v: String -> label = v }), Triple("数字", number, { v: String -> number = v }),
        Triple("单位", unit, { v: String -> unit = v })).forEachIndexed { index, (name, value, change) ->
        OutlinedTextField(value, change, Modifier.fillMaxWidth().testTag("reading-size-${region.id}-$index"),
            label = { Text("$name 基础字号") }, singleLine = true, isError = parse(value) == null)
    }
    TextButton(enabled = valid, onClick = { onChange(ReadingTextSizes(parse(label)!!, parse(number)!!, parse(unit)!!)) },
        modifier = Modifier.testTag("reading-sizes-${region.id}-apply")) { Text("应用三项字号") }
    TextButton(enabled = region.readingTextSizes != null, onClick = { onChange(null) },
        modifier = Modifier.testTag("reading-sizes-${region.id}-reset")) { Text("恢复默认表格字号") }
}
