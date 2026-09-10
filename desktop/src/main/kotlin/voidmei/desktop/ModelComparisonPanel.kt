package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import voidmei.fm.*
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.*

internal data class NamedModel(val aircraft: String, val parameters: FlightModelParameters,
    val jets: List<JetThrustModel> = emptyList(), val source: String? = null,
    val capturedAt: java.time.Instant? = null)

@Composable
internal fun ModelComparisonPanel(baseline: NamedModel, current: NamedModel?, chooseExport: (String) -> String? = ::chooseCsvExport) {
    var sweep by remember { mutableStateOf(0f) }
    var flaps by remember { mutableStateOf(0f) }
    var fuel by remember { mutableStateOf(0f) }
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember(baseline, current, sweep, flaps, fuel) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text("模型比较：${baseline.aircraft} → ${current?.aircraft ?: "等待当前机型"}", style = MaterialTheme.typography.titleMedium)
    baseline.source?.let { Text("基准来源：$it\n快照时间（UTC）：${baseline.capturedAt ?: "未知"}",
        Modifier.testTag("comparison-baseline-source"), style = MaterialTheme.typography.bodySmall) }
    current?.source?.let { Text("当前来源：$it\n快照时间（UTC）：${current.capturedAt ?: "未知"}",
        Modifier.testTag("comparison-current-source"), style = MaterialTheme.typography.bodySmall) }
    Text("基准保留设定时的已加载数据；重新加载当前模型不会更新基准。", style = MaterialTheme.typography.bodySmall)
    Text("共同条件：后掠 ${sweep.roundToInt()}% · 襟翼 ${flaps.roundToInt()}% · 燃油 ${fuel.roundToInt()}%")
    Slider(sweep, { sweep = it }, valueRange = 0f..100f,
        modifier = Modifier.semantics { contentDescription = "比较后掠位置" })
    Slider(flaps, { flaps = it }, valueRange = 0f..100f,
        modifier = Modifier.semantics { contentDescription = "比较襟翼开度" })
    Slider(fuel, { fuel = it }, valueRange = 0f..100f,
        modifier = Modifier.semantics { contentDescription = "比较燃油比例" })
    Text("燃油比例分别乘以各机型的模型容量；0% 表示无燃油。失速为准稳态 1 G 估算，过载使用基础质量模型。", style = MaterialTheme.typography.bodySmall)
    Text("从左到右：基准、当前、当前减基准。差值不代表整机性能优劣；未计损伤和外挂。", style = MaterialTheme.typography.bodySmall)
    if (current == null) Text("等待当前机型模型，比较基准已保留。")
    if (current != null) {
        val rows = remember(baseline, current, sweep, flaps, fuel) {
            ModelComparison.compare(baseline.parameters, current.parameters, sweep / 100.0, flaps.toDouble(), fuel / 100.0)
        }
        TextButton(enabled = !exporting, onClick = {
            val snapshot = modelComparisonCsv(baseline, current, sweep / 100.0, flaps.toDouble(), fuel / 100.0)
            val destination = chooseExport("model-comparison.csv") ?: return@TextButton
            exporting = true; exportMessage = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { exportRecordCsv(java.nio.file.Path.of(destination), snapshot) }
                    exportMessage = "比较已导出：$destination"
                } catch (e: CancellationException) { throw e }
                catch (e: java.nio.file.FileAlreadyExistsException) { exportMessage = "目标文件已存在，请选择新文件名。" }
                catch (e: Exception) { exportMessage = "比较导出失败：${e.message}" }
                finally { exporting = false }
            }
        }) { Text(if (exporting) "正在导出比较…" else "导出参数比较 CSV") }
        Text("导出参数、条件和快照来源；空单元格表示未知，不包含功率或推力曲线。请选择新文件名。", style = MaterialTheme.typography.bodySmall)
        exportMessage?.let { Text(it) }
        fun Double?.display() = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().testTag("model-comparison-${row.label}").semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${row.label} (${row.unit})", Modifier.weight(2f))
                Text(row.baseline.display(), Modifier.weight(1f))
                Text(row.current.display(), Modifier.weight(1f))
                Text(row.delta.display(), Modifier.weight(1f))
            }
        }
    }
    PowerComparisonPanel(baseline, current)
    JetComparisonPanel(baseline, current)
}
