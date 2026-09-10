package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.fm.BlkField

@Composable
internal fun ModelFieldsPanel(fields: List<Pair<String, BlkField>>, filter: String, onFilter: (String) -> Unit) {
    var result by remember(fields, filter) {
        mutableStateOf<List<Pair<String, BlkField>>?>(if (filter.isEmpty()) fields else null)
    }
    LaunchedEffect(fields, filter) {
        if (filter.isNotEmpty()) result = withContext(Dispatchers.Default) {
            fields.filterIndexed { index, field ->
                if (index % 256 == 0) ensureActive()
                field.first.contains(filter, ignoreCase = true)
            }
        }
    }
    OutlinedTextField(filter, onFilter, label = { Text("筛选字段路径") }, singleLine = true)
    val visible = result
    if (visible == null) { Text("正在筛选字段…"); return }
    Text("匹配 ${visible.size} / ${fields.size} 个字段", style = MaterialTheme.typography.bodySmall)
    if (visible.isEmpty()) { Text("没有匹配的字段路径"); return }
    val scroll = key(fields, filter) { rememberLazyListState() }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxWidth().height(240.dp).testTag("model-field-list"), state = scroll,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible) { (path, field) -> Text("$path : ${field.type} = ${field.values.joinToString(", ")}") }
        }
    }
}
