package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import voidmei.recording.*
import java.nio.file.Path

internal fun suggestedFlightPath(enginePath: String): String? = runCatching {
    val path = Path.of(enginePath)
    val name = path.fileName?.toString() ?: return@runCatching null
    val suffix = "-engines.csv"
    if (name.length <= suffix.length || !name.endsWith(suffix, ignoreCase = true)) null
    else path.resolveSibling(name.dropLast(suffix.length) + ".csv").toString()
}.getOrNull()

@Composable
internal fun EngineTimelinePanel(engineText: String, engineIndex: Int, fields: List<Pair<String, String>>,
    initialPath: String = "", chooseFile: (String) -> String? = ::chooseCsvFile) {
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    var source by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<EngineTimelineRecord?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("对应飞行 CSV 路径（用于发动机曲线）") }, singleLine = true)
    TextButton(enabled = !busy, onClick = { chooseFile(path)?.let { path = it } }) { Text("选择对应飞行文件") }
    if (initialPath.isNotEmpty()) Text("已按文件名预填对应飞行日志；关联时仍会校验采样编号和时间。", style = MaterialTheme.typography.bodySmall)
    Button(enabled = !busy && path.isNotBlank(), onClick = {
        val input = path
        busy = true; analysis = null; error = null
        scope.launch {
            try {
                analysis = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    val checkActive = { context.ensureActive() }
                    EngineRecordTimeline.read(readFlightText(Path.of(input), checkActive = checkActive), engineText, engineIndex, checkActive)
                }
                source = input
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "关联失败：${e.message}" }
            finally { busy = false }
        }
    }) { Text("关联并绘制发动机曲线") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    analysis?.let { loaded ->
        val result = loaded.analysis
        Text("对应飞行记录：$source · ${result.summary.aircraft.ifBlank { "未知机型" }}")
        result.notes.forEach { Text(it) }
        key(loaded) { RecordingWindowPanel(loaded.text, result, fields, performance = false) }
    }
}
