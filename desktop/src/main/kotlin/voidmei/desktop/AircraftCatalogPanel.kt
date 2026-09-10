package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.nio.file.Path

@Composable
internal fun AircraftCatalogPanel(root: String, onSelect: (String) -> Unit) {
    var names by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    TextButton(enabled = !busy, onClick = {
        busy = true; error = null; names = null
        scope.launch {
            try {
                names = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    FlightModelRepository(Path.of(root)).listAircraft { context.ensureActive() }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "机型列表读取失败：${e.message}" }
            finally { busy = false }
        }
    }) { Text(if (busy) "正在扫描机型…" else "列出本地机型") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    names?.let { all ->
        OutlinedTextField(filter, { filter = it }, label = { Text("筛选本地机型") }, singleLine = true)
        val visible = remember(all, filter) { all.filter { it.contains(filter, ignoreCase = true) } }
        Text("匹配 ${visible.size} / ${all.size} 个本地机型", style = MaterialTheme.typography.bodySmall)
        Text("列表来自中央 .blkx 文件名，模型是否完整在打开时校验。", style = MaterialTheme.typography.bodySmall)
        if (visible.isEmpty()) Text("没有匹配的本地机型")
        else key(all, filter) {
            LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                items(visible, key = { it }) { name -> TextButton(onClick = { onSelect(name) }) { Text("打开 $name") } }
            }
        }
    }
}
