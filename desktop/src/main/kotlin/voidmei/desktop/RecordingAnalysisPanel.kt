package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.recording.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

internal fun readFlightText(path: Path, charset: java.nio.charset.Charset = Charsets.UTF_8, checkActive: () -> Unit = {}): String {
    checkActive()
    require(Files.isRegularFile(path)) { "请选择普通 CSV 文件" }
    return Files.newInputStream(path).use {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        checkActive()
        val count = it.read(buffer)
        if (count < 0) break
        require(output.size() + count <= FlightRecordReader.MAX_BYTES) { "记录超过 64 MiB 限制" }
        output.write(buffer, 0, count)
    }
    val bytes = output.toByteArray()
    checkActive()
    val text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    checkActive()
    text
    }
}
internal fun readFlightSummary(path: Path): FlightRecordSummary = FlightRecordReader.summarize(readFlightText(path))
internal fun readFlightAnalysis(path: Path, legacy: Boolean = false, gb18030: Boolean = false): FlightRecordAnalysis {
    val text = readFlightText(path, if (legacy && gb18030) java.nio.charset.Charset.forName("GB18030") else Charsets.UTF_8)
    return if (legacy) LegacyFlightRecordReader.analyze(text) else FlightRecordPlots.analyze(text)
}

@Composable
internal fun RecordingAnalysisPanel(chooseFile: (String) -> String? = ::chooseCsvFile, latestFile: String? = null) {
    var snapshot by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<FlightRecordSummary?>(null) }
    var plots by remember { mutableStateOf<Map<String, List<RecordedPlotPoint>>>(emptyMap()) }
    var source by remember { mutableStateOf("") }
    var sourceFormat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var legacy by remember { mutableStateOf(false) }
    var gb18030 by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    Text("记录回看", style = MaterialTheme.typography.titleLarge)
    latestFile?.let { latest ->
        TextButton(enabled = !busy, onClick = { path = latest; legacy = false; gb18030 = false }) {
            Text("使用最近完成的飞行记录")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!legacy, { legacy = false }, label = { Text("Kotlin CSV") })
        FilterChip(legacy, { legacy = true }, label = { Text("旧版中文 CSV") })
    }
    if (legacy) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("旧文件编码")
        FilterChip(!gb18030, { gb18030 = false }, label = { Text("UTF-8") })
        FilterChip(gb18030, { gb18030 = true }, label = { Text("GB18030 / GBK") })
    }
    if (legacy) Text("此旧格式的时间列按分钟换算。仅用于原版中文表头、实际按分钟写入的日志。",
        style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(path, { path = it }, Modifier.weight(1f), label = { Text("飞行 CSV 文件路径") }, singleLine = true)
        TextButton(enabled = !busy, onClick = { chooseFile(path)?.let { path = it } }) { Text("选择飞行文件") }
        Button(enabled = !busy && path.isNotBlank(), onClick = {
            val selected = path
            val selectedLegacy = legacy
            val selectedEncoding = gb18030
            busy = true; snapshot = ""; summary = null; plots = emptyMap(); notes = emptyList(); error = null
            scope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val context = currentCoroutineContext()
                        val checkActive = { context.ensureActive() }
                        val original = readFlightText(Path.of(selected), if (selectedLegacy && selectedEncoding) java.nio.charset.Charset.forName("GB18030") else Charsets.UTF_8, checkActive)
                        val normalized = if (selectedLegacy) LegacyFlightRecordReader.normalize(original, checkActive) else original
                        normalized to FlightRecordPlots.analyze(normalized, checkActive = checkActive).let { if (selectedLegacy) it.copy(notes = LegacyFlightRecordReader.notes) else it }
                    }
                    snapshot = loaded.first
                    val result = loaded.second
                    summary = result.summary
                    plots = result.plots
                    notes = result.notes
                    source = selected
                    sourceFormat = if (selectedLegacy) "旧版中文 CSV · ${if (selectedEncoding) "GB18030 / GBK" else "UTF-8"} · 时间按分钟转换"
                        else "Kotlin CSV · UTF-8"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "无法读取记录" }
                finally { busy = false }
            }
        }) { Text(if (busy) "读取中…" else "读取摘要") }
    }
    error?.let { Text("读取失败：$it", color = MaterialTheme.colorScheme.error) }
    summary?.let { record ->
        Text("${record.aircraft.ifBlank { "未知机型" }} · ${record.samples} 帧 · 采样跨度 ${record.spanMs / 1000.0} s")
        Text("${record.startEpochMs?.let(Instant::ofEpochMilli) ?: "未知"} — ${record.endEpochMs?.let(Instant::ofEpochMilli) ?: "未知"} · UTC")
        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(source, style = MaterialTheme.typography.bodySmall)
        Text(sourceFormat, style = MaterialTheme.typography.bodySmall)
        var allRanges by remember(record) { mutableStateOf(false) }
        TextButton(onClick = { allRanges = !allRanges }) { Text(if (allRanges) "收起其他字段摘要" else "展开所有字段摘要") }
        val fields = (if (allRanges) RecordedField.entries else RecordedField.quick).map { it.id to it.label }
        fields.forEach { (key, label) ->
            val range = record.ranges[key]
            Text(if (range == null) "$label：无有效数据" else
                "$label：${String.format(Locale.ROOT, "%.2f–%.2f", range.minimum, range.maximum)} · 有效 ${range.count}/${record.samples} 帧")
        }
        Text("摘要为已读取记录的最小/最大值；采样跨度包含记录间隔，不等于连续有效飞行时间。", style = MaterialTheme.typography.bodySmall)
        key(snapshot) { RecordingWindowPanel(snapshot, FlightRecordAnalysis(record, plots, notes)) }
    }
}
