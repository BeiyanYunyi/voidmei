package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
internal fun NumberFontSettings(name: String?, onChange: (String?) -> Unit) {
    var draft by remember(name) { mutableStateOf(name.orEmpty()) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    val resolved = remember(name) { resolveHudNumberFont(name) }
    OutlinedTextField(draft, { draft = it; error = null }, singleLine = true, label = { Text("全局数字字体") }, isError = error != null)
    TextButton(onClick = {
        val next = draft.trim().takeIf { it.isNotEmpty() }
        if (next != null && (next.length > 200 || next.any { it.isISOControl() })) error = "字体名称需为不超过 200 字的单行文本"
        else { error = null; onChange(next) }
    }) { Text("应用数字字体") }
    Text("用于飞行与发动机表格的读数；HUD 优先使用专用数字字体，未指定时继承此设置。留空使用默认等宽字体。", style = MaterialTheme.typography.bodySmall)
    if (resolved.unavailable) Text("未找到数字字体 $name，已回退默认等宽字体。", color = MaterialTheme.colorScheme.error)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
