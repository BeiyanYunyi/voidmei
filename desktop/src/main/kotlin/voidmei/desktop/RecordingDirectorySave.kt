package voidmei.desktop

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.nio.file.Path

@Composable
internal fun RecordingDirectorySave(saved: String, draft: String, enabled: Boolean, onSave: (String) -> Unit) {
    var error by remember(draft) { mutableStateOf<String?>(null) }
    TextButton(enabled = enabled && draft.isNotBlank(), onClick = {
        try {
            val path = Path.of(draft).toAbsolutePath().normalize().toString()
            onSave(path)
            error = null
        } catch (e: Exception) { error = "目录路径无效：${e.message}" }
    }) { Text("保存记录目录") }
    SelectionContainer { Text("已配置的记录目录：$saved", style = MaterialTheme.typography.bodySmall) }
    Text("自动记录使用已配置目录；保存目录不启动记录，实际可写性在开启记录时检查。", style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
