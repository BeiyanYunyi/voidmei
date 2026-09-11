package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.recording.*
import kotlin.math.roundToLong

@Composable
internal fun RecordingWindowPanel(text: String, full: FlightRecordAnalysis,
    fields: List<Pair<String, String>> = RecordedField.entries.map { it.id to it.label },
    chooseExport: (String) -> String? = ::chooseCsvExport,
    performance: Boolean = true,
    analyzeWindow: suspend (String, Long, Long, List<String>) -> RecordWindowAnalysis = { source, start, end, columns ->
        val context = currentCoroutineContext()
        FlightRecordWindow.analyze(source, start, end, columns) { context.ensureActive() }
    }) {
    key(text, fields.map { it.first }) {
        RecordingWindowContent(text, full, fields, chooseExport, analyzeWindow, performance)
    }
}

@Composable
private fun RecordingWindowContent(text: String, full: FlightRecordAnalysis,
    fields: List<Pair<String, String>>, chooseExport: (String) -> String?,
    analyzeWindow: suspend (String, Long, Long, List<String>) -> RecordWindowAnalysis, performance: Boolean) {
    var seek by remember(text) { mutableStateOf<ReplaySeekRequest?>(null) }
    var serial by remember(text) { mutableStateOf(0) }
    var replayFrame by remember(text) { mutableStateOf<RecordedFrame?>(null) }
    var start by remember(text) { mutableStateOf("0") }
    var end by remember(text) { mutableStateOf((full.summary.spanMs / 1000.0).toString()) }
    var window by remember(text) { mutableStateOf<RecordWindowAnalysis?>(null) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportMessage by remember(text) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var analysisGeneration by remember { mutableStateOf(0L) }
    fun analyzeRange(from: String, to: String) {
        if (busy) return
        error = null
        busy = true
        val generation = ++analysisGeneration
        analysisJob = scope.launch {
            try {
                fun milliseconds(value: String): Long {
                    val seconds = value.toDoubleOrNull()
                    require(seconds != null && seconds.isFinite() && seconds >= 0 && seconds * 1000 < Long.MAX_VALUE.toDouble()) { "时间必须为非负有限秒数" }
                    return (seconds * 1000).roundToLong()
                }
                val begin = milliseconds(from)
                val finish = milliseconds(to)
                val result = withContext(Dispatchers.Default) { analyzeWindow(text, begin, finish, fields.map { it.first }) }
                if (generation == analysisGeneration) window = result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == analysisGeneration) error = "区间分析失败：${e.message}" }
            finally { if (generation == analysisGeneration) { busy = false; analysisJob = null } }
        }
    }
    RecordingReplayPanel(text, fields, seek,
        window = window?.let { it.firstPointOffsetMs..(it.firstPointOffsetMs + it.analysis.summary.spanMs) }
            ?: (0L..full.summary.spanMs)) { replayFrame = it; if (it == null) seek = null }
    Text("时间区间（相对原记录首帧，秒）")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it }, Modifier.weight(1f), enabled = !busy, label = { Text("区间起点 (s)") }, singleLine = true)
        OutlinedTextField(end, { end = it }, Modifier.weight(1f), enabled = !busy, label = { Text("区间终点 (s)") }, singleLine = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = { analyzeRange(start, end) }) { Text("分析此区间") }
        TextButton(onClick = {
            analysisGeneration++
            analysisJob?.cancel(); analysisJob = null; busy = false
            window = null; error = null; start = "0"; end = (full.summary.spanMs / 1000.0).toString()
        }) { Text("恢复完整记录") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val result = window
    val analysis = result?.analysis ?: full
    val offset = result?.firstPointOffsetMs ?: 0
    Text("当前曲线：${offset / 1000.0}–${(offset + analysis.summary.spanMs) / 1000.0} s · ${analysis.summary.samples} 帧")
    Text("区间内从原始记录重新统计和分桶；整段摘要保持不变。", style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !busy && !exportBusy, onClick = {
        val selectedText = result?.text ?: text
        val destination = chooseExport("flight-selection.csv") ?: return@TextButton
        exportBusy = true; exportMessage = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { exportRecordCsv(java.nio.file.Path.of(destination), selectedText) }
                exportMessage = "已导出：$destination"
            } catch (e: CancellationException) { throw e }
            catch (e: java.nio.file.FileAlreadyExistsException) { exportMessage = "目标文件已存在，请选择新文件名。" }
            catch (e: Exception) { exportMessage = "导出失败：${e.message}。请检查目录权限及文件系统是否支持硬链接。" }
            finally { exportBusy = false }
        }
    }) { Text(if (exportBusy) "正在导出…" else "导出当前区间 CSV") }
    Text("导出已读取的数据，保留采样编号和时间列；旧版日志输出为转换后的 CSV。请选择新文件名。", style = MaterialTheme.typography.bodySmall)
    exportMessage?.let { Text(it) }
    if (performance) key(result?.text ?: text) { PerformanceAnalysisPanel(result?.text ?: text, chooseExport) }
    RecordingPlotPanel(analysis.summary, analysis.plots, fields, offset, replayFrame = replayFrame,
        onPointSelected = if (replayFrame == null) null else { point ->
            point.sampleId?.let { seek = ReplaySeekRequest(++serial, it) }
        },
        onRangeSelected = if (busy) null else { first, last ->
            start = (first / 1000.0).toString()
            end = (last / 1000.0).toString()
            analyzeRange(start, end)
        })
}
