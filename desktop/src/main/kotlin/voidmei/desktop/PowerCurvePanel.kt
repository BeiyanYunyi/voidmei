package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voidmei.fm.*
import java.util.Locale
import kotlin.math.roundToInt

internal const val POWER_CURVE_STEP_M = 25

private data class PowerCurves(val military: List<PistonPowerPoint?>, val wep: List<PistonPowerPoint?>?)

@Composable
internal fun PowerCurvePanel(model: PistonModels, fuelLabel: String = "未选择燃油修正") {
    var expanded by rememberSaveable(model) { mutableStateOf(false) }
    var equivalentAirspeed by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable(model) { mutableStateOf(0.0) }
    var temperature by rememberSaveable(model) { mutableStateOf(15.0) }
    var probe by rememberSaveable(model) { mutableStateOf(0f) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起功率曲线" else "展开功率曲线") }
    if (!expanded) return
    var curves by remember(model, speed, temperature, equivalentAirspeed) { mutableStateOf<PowerCurves?>(null) }
    LaunchedEffect(model, speed, temperature, equivalentAirspeed) {
        curves = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            PowerCurves(PistonPowerModel.curve(model.military.stages, speedKmh = speed, equivalentAirspeed = equivalentAirspeed, seaLevelTempC = temperature, stepM = POWER_CURVE_STEP_M, checkActive = { context.ensureActive() }),
                model.wepStages?.let { PistonPowerModel.curve(it, wep = true, speedKmh = speed, equivalentAirspeed = equivalentAirspeed, seaLevelTempC = temperature, stepM = POWER_CURVE_STEP_M, checkActive = { context.ensureActive() }) })
        }
    }
    PowerSpeedTypePanel(equivalentAirspeed) { equivalentAirspeed = it }
    Text("高度–功率 · 单台发动机 · $fuelLabel")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (equivalentAirspeed) "EAS" else "TAS")
        listOf(0.0, 300.0, 600.0).forEach { value ->
            FilterChip(speed == value, { speed = value }, label = { Text("${value.toInt()} km/h") })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("海平面温度")
        listOf(-10.0, 15.0, 30.0).forEach { value ->
            FilterChip(temperature == value, { temperature = value }, label = { Text("${value.toInt()}°C") })
        }
    }
    PowerConditionsPanel(speed, temperature, equivalentAirspeed) { nextSpeed, nextTemperature ->
        speed = nextSpeed; temperature = nextTemperature
    }
    val current = curves
    if (current == null) { Text("正在计算曲线…"); return }
    PowerCurveSummaryPanel("军用", current.military)
    current.wep?.let { PowerCurveSummaryPanel("WEP", it) }
    if (current.military.all { it == null }) Text("当前条件下无有效军用功率数据")
    if (current.wep != null && current.wep.all { it == null }) Text("当前条件下无有效 WEP 功率数据")
    val militaryColor = Color(0xFF84DEC6)
    val wepColor = Color(0xFFFFBB66)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val ceiling = (current.military + current.wep.orEmpty()).mapNotNull { it?.powerHp }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Text("军用（青色）" + if (current.wep != null) " · WEP（橙色）" else " · WEP 不可用")
    Text("${hp(ceiling)} hp/台")
    Text("▲ 峰值 · ▼ 谷值 · ◆ 斜率转折（颜色对应曲线）", style = MaterialTheme.typography.bodySmall)
    Text("点击图表查看对应高度；也可使用下方滑块。", style = MaterialTheme.typography.bodySmall)
    val militaryExtrema = remember(current) { PowerCurveSummary.from(current.military).extrema }
    val wepExtrema = remember(current) { PowerCurveSummary.from(current.wep.orEmpty()).extrema }
    Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("power-curve-plot")
        .powerAltitudePicker { probe = it }
        .semantics { contentDescription = "0 至 10000 米的单台发动机功率曲线；缺失值处断开" }) {
        repeat(5) { i ->
            val y = size.height * i / 4
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
        }
        fun series(points: List<PistonPowerPoint?>, color: Color) {
            val path = Path()
            var connected = false
            for (point in points) {
                if (point == null) { connected = false; continue }
                val x = (point.altitudeM / 10000.0).toFloat() * size.width
                val y = (1 - point.powerHp / ceiling).toFloat() * size.height
                if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                connected = true
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        series(current.military, militaryColor)
        current.wep?.let { series(it, wepColor) }
        drawPowerExtrema(militaryExtrema, ceiling, militaryColor)
        drawPowerExtrema(wepExtrema, ceiling, wepColor)
        val x = probe / 10000f * size.width
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0 hp · 0 m"); Text("5000 m"); Text("10000 m")
    }
    Slider(probe, { probe = (it / POWER_CURVE_STEP_M).roundToInt() * POWER_CURVE_STEP_M.toFloat() }, valueRange = 0f..10000f, steps = 10000 / POWER_CURVE_STEP_M - 1,
        modifier = Modifier.testTag("power-curve-altitude").semantics { contentDescription = "查看高度" })
    val index = (probe / POWER_CURVE_STEP_M).roundToInt()
    val military = current.military.getOrNull(index)
    val wep = current.wep?.getOrNull(index)
    Text("高度 ${probe.toInt()} m · 军用 ${hp(military?.powerHp)} hp · WEP ${hp(wep?.powerHp)} hp")
    Text("增压器级：军用 ${military?.stageIndex?.plus(1) ?: "—"} · WEP ${wep?.stageIndex?.plus(1) ?: "—"}")
    if ((current.military + current.wep.orEmpty()).any { it == null }) Text("部分高度无法计算，曲线在缺失处断开。")
}

private fun hp(value: Double?) = value?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "—"
