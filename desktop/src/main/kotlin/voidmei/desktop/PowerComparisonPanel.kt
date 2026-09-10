package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.fm.*
import java.util.Locale
import kotlin.math.roundToInt

private data class ComparedPower(val baseline: List<PistonPowerPoint?>?, val current: List<PistonPowerPoint?>?)

@Composable
internal fun PowerComparisonPanel(baseline: NamedModel, current: NamedModel?) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLeft by remember { mutableStateOf<Int?>(null) }
    var selectedRight by remember { mutableStateOf<Int?>(null) }
    var wep by remember { mutableStateOf(false) }
    var equivalentAirspeed by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(0.0) }
    var temperature by remember { mutableStateOf(15.0) }
    var altitude by remember { mutableStateOf(0f) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起功率叠加比较" else "展开功率叠加比较") }
    if (!expanded) return
    if (current == null) { Text("等待当前模型，功率比较条件已保留。"); return }
    val leftEngines = baseline.parameters.engineCompressors
    val rightEngines = current.parameters.engineCompressors
    val leftIndex = selectedLeft?.takeIf { it in leftEngines } ?: leftEngines.keys.minOrNull()
    val rightIndex = selectedRight?.takeIf { it in rightEngines } ?: rightEngines.keys.minOrNull()
    val left = leftIndex?.let(leftEngines::get)
    val right = rightIndex?.let(rightEngines::get)
    var curves by remember(left, right, wep, speed, temperature, equivalentAirspeed) { mutableStateOf<ComparedPower?>(null) }
    LaunchedEffect(left, right, wep, speed, temperature, equivalentAirspeed) {
        curves = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            fun curve(model: PistonModels?): List<PistonPowerPoint?>? {
                val stages = if (wep) model?.wepStages else model?.military?.stages
                return stages?.let { PistonPowerModel.curve(it, wep, speedKmh = speed, equivalentAirspeed = equivalentAirspeed, seaLevelTempC = temperature, stepM = POWER_CURVE_STEP_M, checkActive = { context.ensureActive() }) }
            }
            ComparedPower(curve(left), curve(right))
        }
    }
    PowerSpeedTypePanel(equivalentAirspeed) { equivalentAirspeed = it }
    Text("基准 ${baseline.aircraft}（青色） · 当前 ${current.aircraft}（橙色） · hp/台")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leftEngines.keys.sorted().forEach { id -> FilterChip(leftIndex == id, { selectedLeft = id }, label = { Text("基准发动机 #$id") }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rightEngines.keys.sorted().forEach { id -> FilterChip(rightIndex == id, { selectedRight = id }, label = { Text("当前发动机 #$id") }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wep, { wep = false }, label = { Text("军用比较") })
        FilterChip(wep, { wep = true }, label = { Text("WEP 比较") })
        listOf(0.0, 300.0, 600.0).forEach { value ->
            FilterChip(speed == value, { speed = value }, label = { Text("${if (equivalentAirspeed) "EAS" else "TAS"} ${value.toInt()} km/h") })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-10.0, 15.0, 30.0).forEach { value ->
            FilterChip(temperature == value, { temperature = value }, label = { Text("海平面 ${value.toInt()}°C") })
        }
    }
    PowerConditionsPanel(speed, temperature, equivalentAirspeed) { nextSpeed, nextTemperature ->
        speed = nextSpeed; temperature = nextTemperature
    }
    Text("燃油修正：基准 ${baseline.parameters.compressorFuel?.id ?: "未追加"} · 当前 ${current.parameters.compressorFuel?.id ?: "未追加"}",
        style = MaterialTheme.typography.bodySmall)
    val data = curves ?: run { Text("正在计算比较曲线…"); return }
    data.baseline?.let { PowerCurveSummaryPanel("基准", it) }
    data.current?.let { PowerCurveSummaryPanel("当前", it) }
    if (data.baseline == null) Text("基准：${if (wep && left != null) "WEP 不可用" else "无可用活塞发动机模型"}")
    if (data.current == null) Text("当前：${if (wep && right != null) "WEP 不可用" else "无可用活塞发动机模型"}")
    if (data.baseline != null && data.baseline.all { it == null }) Text("基准在当前条件下无有效功率数据")
    if (data.current != null && data.current.all { it == null }) Text("当前在当前条件下无有效功率数据")
    val ceiling = (data.baseline.orEmpty() + data.current.orEmpty()).mapNotNull { it?.powerHp }.maxOrNull()?.coerceAtLeast(1.0)
    if (ceiling == null) return
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Text("▲ 峰值 · ▼ 谷值 · ◆ 斜率转折（颜色对应曲线）", style = MaterialTheme.typography.bodySmall)
    Text("点击图表查看对应高度；也可使用下方滑块。", style = MaterialTheme.typography.bodySmall)
    val baselineExtrema = remember(data) { voidmei.fm.PowerCurveSummary.from(data.baseline.orEmpty()).extrema }
    val currentExtrema = remember(data) { voidmei.fm.PowerCurveSummary.from(data.current.orEmpty()).extrema }
    Canvas(Modifier.fillMaxWidth().height(240.dp).testTag("power-comparison-plot")
        .powerAltitudePicker { altitude = it }
        .semantics { contentDescription = "共同坐标下 0 至 10000 米的单台发动机功率比较" }) {
        repeat(5) { i -> val y = size.height * i / 4; drawLine(grid, Offset(0f, y), Offset(size.width, y)) }
        fun series(points: List<PistonPowerPoint?>?, color: Color) {
            val path = Path()
            var connected = false
            points?.forEach { point ->
                if (point == null) connected = false else {
                    val x = (point.altitudeM / 10000 * size.width).toFloat()
                    val y = ((1 - point.powerHp / ceiling) * size.height).toFloat()
                    if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                    connected = true
                }
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        series(data.baseline, Color(0xFF84DEC6)); series(data.current, Color(0xFFFFBB66))
        drawPowerExtrema(baselineExtrema, ceiling, Color(0xFF84DEC6))
        drawPowerExtrema(currentExtrema, ceiling, Color(0xFFFFBB66))
        val x = altitude / 10000 * size.width
        drawLine(grid, Offset(x, 0f), Offset(x, size.height))
    }
    fun Double?.hp() = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
    Text("纵轴 0–${ceiling.hp()} hp/台 · 横轴 0–10000 m")
    Slider(altitude, { altitude = (it / POWER_CURVE_STEP_M).roundToInt() * POWER_CURVE_STEP_M.toFloat() }, valueRange = 0f..10000f, steps = 10000 / POWER_CURVE_STEP_M - 1,
        modifier = Modifier.semantics { contentDescription = "功率比较高度" })
    val index = (altitude / POWER_CURVE_STEP_M).roundToInt()
    Text("高度 ${altitude.toInt()} m · 基准 ${data.baseline?.getOrNull(index)?.powerHp.hp()} hp · 当前 ${data.current?.getOrNull(index)?.powerHp.hp()} hp")
    Text("按各发动机最佳增压器档位估算；缺失点断开，不合计整机功率，不计损伤和实时油门。", style = MaterialTheme.typography.bodySmall)
}
