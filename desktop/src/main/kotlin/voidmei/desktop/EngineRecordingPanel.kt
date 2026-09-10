package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.recording.*
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

private data class LoadedEngineRecording(val text: String, val source: String, val records: List<EngineRecordSummary>)

@Composable
internal fun EngineRecordingPanel(chooseFile: (String) -> String? = ::chooseCsvFile, latestFile: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("发动机记录分析") }
    if (!expanded) return
    var path by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf<LoadedEngineRecording?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    latestFile?.let { latest ->
        TextButton(enabled = !busy, onClick = { path = latest }) { Text("使用最近完成的发动机记录") }
    }
    Text("读取 Kotlin 版保存的发动机 CSV，按编号分别统计。该文件没有机型和单调时间，不能单独推算飞行时长。")
    OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("发动机 CSV 文件路径") }, singleLine = true)
    TextButton(enabled = !busy, onClick = { chooseFile(path)?.let { path = it } }) { Text("选择发动机文件") }
    Button(enabled = !busy && path.isNotBlank(), onClick = {
        val input = path
        loaded = null; selected = null; menu = false; error = null; busy = true
        scope.launch {
            try {
                loaded = withContext(Dispatchers.IO) {
                    val text = readFlightText(Path.of(input))
                    LoadedEngineRecording(text, input, EngineRecordReader.summarize(text))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "发动机记录读取失败：${e.message}" }
            finally { busy = false }
        }
    }) { Text("读取发动机摘要") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val current = loaded ?: return
    val records = current.records
    val record = records.firstOrNull { it.index == selected } ?: records.firstOrNull() ?: return
    Text("来源：${current.source}")
    Box {
        TextButton(onClick = { menu = true }) { Text("选择发动机 #${record.index}") }
        DropdownMenu(menu, { menu = false }) {
            records.forEach { record ->
                DropdownMenuItem(text = { Text("发动机 #${record.index}") }, onClick = { selected = record.index; menu = false })
            }
        }
    }
    Text("发动机 #${record.index} · ${record.samples} 条记录 · 采样编号 ${record.firstSampleId}–${record.lastSampleId}")
    Text("${Instant.ofEpochMilli(record.firstEpochMs)} — ${Instant.ofEpochMilli(record.lastEpochMs)} · UTC（原始记录时间）")
    val fields = listOf("throttle_percent" to "油门 (%)", "rpm" to "转速 (RPM)", "power_hp" to "功率 (hp)",
        "thrust_kgf" to "推力 (kgf)", "water_temp_c" to "水温 (°C)", "oil_temp_c" to "油温 (°C)",
        "rpm_control_percent" to "转速控制 (%)", "mixture_percent" to "混合比 (%)", "radiator_percent" to "散热器 (%)",
        "oil_radiator_percent" to "滑油散热器 (%)", "compressor_stage" to "增压器档位", "magneto" to "磁电机",
        "manifold_pressure_atm" to "进气压力 (atm)", "propeller_pitch_deg" to "桨距 (°)", "efficiency_percent" to "效率 (%)")
    val flightPath = remember(current.source) { suggestedFlightPath(current.source).orEmpty() }
    key(current, record.index) { EngineTimelinePanel(current.text, record.index, fields, initialPath = flightPath) }
    fields.forEach { (key, label) ->
        val range = record.ranges[key]
        Text(if (range == null) "$label：无有效数据" else
            "$label：${String.format(Locale.ROOT, "%.2f–%.2f", range.minimum, range.maximum)} · 有效 ${range.count}/${record.samples} 条")
    }
}
