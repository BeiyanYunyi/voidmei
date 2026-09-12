package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun ReadingColumnsInput(columns: Int?, tag: String, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("$tag-toggle")) {
        Text(if (expanded) "收起自定义列数" else "自定义列数（${columns?.takeIf { it > 0 } ?: "未指定"}）")
    }
    if (!expanded) return
    var input by remember(columns) { mutableStateOf(columns?.takeIf { it > 0 }?.toString() ?: "") }
    val parsed = input.toIntOrNull()?.takeIf { it in 1..16 }
    OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("固定列数（1–16）") },
        isError = parsed == null, modifier = Modifier.testTag(tag),
        supportingText = { Text("多列适合较宽区域；过窄时请减小列数或字号。") })
    TextButton(enabled = parsed != null && parsed != columns, onClick = { parsed?.let(onChange) },
        modifier = Modifier.testTag("$tag-apply")) { Text("应用列数") }
}
