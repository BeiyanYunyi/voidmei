package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.recording.*
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PerformanceAnalysisPanel(text: String, chooseExport: (String) -> String? = ::chooseCsvExport,
    analyze: suspend (String) -> FlightPerformanceAnalysis = { source ->
        val context = currentCoroutineContext()
        FlightPerformanceAnalyzer.analyze(source) { context.ensureActive() }
    }) {
    key(text) { PerformanceAnalysisContent(text, chooseExport, analyze) }
}

@Composable
private fun PerformanceAnalysisContent(text: String, chooseExport: (String) -> String?,
    analyze: suspend (String) -> FlightPerformanceAnalysis) {
    var result by remember(text) { mutableStateOf<FlightPerformanceAnalysis?>(null) }
    var busy by remember(text) { mutableStateOf(false) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    var message by remember(text) { mutableStateOf<String?>(null) }
    var kind by remember(text) { mutableStateOf(0) }
    var fraction by remember(text, kind) { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0L) }
    TextButton(enabled = !busy, onClick = {
        busy = true; error = null
        val current = ++generation
        analysisJob = scope.launch {
            try {
                val computed = withContext(Dispatchers.Default) { analyze(text) }
                if (generation == current) result = computed
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == current) error = e.message ?: "分析失败" }
            finally { if (generation == current) { busy = false; analysisJob = null } }
        }
    }) { Text(if (analysisJob != null) "正在统计性能采样…" else "统计爬升与机动采样") }
    if (analysisJob != null) TextButton(onClick = {
        generation++; analysisJob?.cancel(); analysisJob = null; busy = false
    }) { Text("取消统计") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val analysis = result ?: return
    Text("高度档 ${analysis.climb.size} · 滚转速度档 ${analysis.roll.size} · 过载速度档 ${analysis.turn.size}")
    Text("使用当前区间原始样本。只显示实际观测档位，不补零或插值；过载沿用旧版平滑规则，不是飞机极限。", style = MaterialTheme.typography.bodySmall)
    Text("到档时间从所选区间首帧起算，包含下降和数据缺失期间；不是连续爬升用时。", style = MaterialTheme.typography.bodySmall)
    val labels = listOf("到档时间", "爬升功率", "爬升推力", "爬升 SEP", "滚转率", "平滑过载", "机动 SEP")
    FlowRow { labels.forEachIndexed { index, label -> FilterChip(kind == index, { kind = index }, label = { Text(label) }) } }
    val points: List<Pair<Double, Double>> = when (kind) {
        0 -> analysis.climb.map { it.altitudeM.toDouble() to it.elapsedMs / 1000.0 }
        1 -> analysis.climb.mapNotNull { p -> p.powerHp?.let { p.altitudeM.toDouble() to it } }
        2 -> analysis.climb.mapNotNull { p -> p.thrustKgf?.let { p.altitudeM.toDouble() to it } }
        3 -> analysis.climb.mapNotNull { p -> p.sepMps?.let { p.altitudeM.toDouble() to it } }
        4 -> analysis.roll.map { it.iasKmh.toDouble() to it.rateDegps }
        5 -> analysis.turn.map { it.iasKmh.toDouble() to it.loadG }
        else -> analysis.turn.map { it.iasKmh.toDouble() to it.sepMps }
    }
    val xUnit = if (kind < 4) "m" else "km/h"
    val yUnit = listOf("s", "hp", "kgf", "m/s", "°/s", "G", "m/s")[kind]
    if (points.isEmpty()) Text("此项没有有效采样") else {
        val scale = points.maxOf { abs(it.second) }.coerceAtLeast(1.0)
        val low = points.minOf { it.second / scale }
        val high = points.maxOf { it.second / scale }
        val first = points.first().first
        val last = points.last().first
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("performance-plot")) {
            points.forEach { (x, y) ->
                val px = if (first == last) .5 else (x - first) / (last - first)
                val py = if (low == high) .5 else (y / scale - low) / (high - low)
                drawCircle(color, 3.dp.toPx(), Offset((4 + px * (size.width - 8)).toFloat(), (4 + (1 - py) * (size.height - 8)).toFloat()))
            }
        }
        fun Double.fmt() = String.format(Locale.ROOT, "%.2f", this)
        Text("横轴 ${first.fmt()}–${last.fmt()} $xUnit · 纵轴 ${(low * scale).fmt()}–${(high * scale).fmt()} $yUnit")
        if (points.size > 1) Slider(fraction, { fraction = it }, Modifier.testTag("performance-point"))
        val point = points[(fraction * (points.size - 1)).roundToInt()]
        Text("所选采样：${point.first.fmt()} $xUnit · ${point.second.fmt()} $yUnit")
    }
    TextButton(enabled = !busy, onClick = {
        val destination = chooseExport("flight-performance.csv") ?: return@TextButton
        busy = true; message = null
        scope.launch {
            try { withContext(Dispatchers.IO) { exportRecordCsv(Path.of(destination), analysis.csv()) }; message = "已导出性能采样：$destination" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "导出失败：${e.message}。请选择不存在的新文件名。" }
            finally { busy = false }
        }
    }) { Text("导出性能采样 CSV") }
    message?.let { Text(it) }
}
