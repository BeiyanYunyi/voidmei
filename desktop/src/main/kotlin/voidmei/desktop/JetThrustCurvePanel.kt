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
import voidmei.fm.JetThrustModel
import java.util.Locale

@Composable
internal fun JetThrustCurvePanel(model: JetThrustModel) {
    var expanded by rememberSaveable(model) { mutableStateOf(false) }
    var heightFraction by rememberSaveable(model) { mutableStateOf(0f) }
    var speedFraction by rememberSaveable(model) { mutableStateOf(0f) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起推力曲线" else "展开推力曲线") }
    JetAltitudeComparisonPanel(model)
    if (!expanded) return
    val altitude = model.altitudesM.first() * (1 - heightFraction.toDouble()) + model.altitudesM.last() * heightFraction.toDouble()
    val speed = model.velocitiesKmh.first() * (1 - speedFraction.toDouble()) + model.velocitiesKmh.last() * speedFraction.toDouble()
    val military = remember(model, altitude) { model.velocitiesKmh.map { model.thrust(altitude, it) } }
    val afterburner = remember(model, altitude) { model.afterburnerKgf?.let { model.velocitiesKmh.map { model.thrust(altitude, it, true) } } }
    val ceiling = (military + afterburner.orEmpty()).filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Text("速度–推力 · 单台发动机 · FM 表内估算")
    Text("查看高度 ${thrustNumber(altitude)} m")
    if (model.altitudesM.size > 1) Slider(heightFraction, { heightFraction = it },
        modifier = Modifier.testTag("jet-curve-altitude").semantics { contentDescription = "推力曲线高度" })
    Text("军用（青色）" + if (afterburner != null) " · 加力（橙色）" else " · 加力不可用")
    Text("${thrustNumber(ceiling)} kgf/台")
    Text("点击图表查看对应速度；也可使用下方滑块。", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("jet-curve-plot")
        .chartFractionPicker { speedFraction = it }
        .semantics { contentDescription = "所选高度下的军用与加力推力曲线；缺失值断开" }) {
        repeat(5) { i -> drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
        fun series(values: List<Double?>, color: Color) {
            val path = Path()
            var connected = false
            values.forEachIndexed { index, value ->
                if (value == null) { connected = false; return@forEachIndexed }
                val fraction = if (model.velocitiesKmh.size == 1) 0.5 else
                    (model.velocitiesKmh[index] - model.velocitiesKmh.first()) / (model.velocitiesKmh.last() - model.velocitiesKmh.first())
                val point = Offset(fraction.toFloat() * size.width, (1 - value / ceiling).toFloat() * size.height)
                if (connected) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                connected = true
                drawCircle(color, 2.dp.toPx(), point)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        series(military, Color(0xFF84DEC6))
        afterburner?.let { series(it, Color(0xFFFFBB66)) }
        val x = (if (model.velocitiesKmh.size == 1) 0.5f else speedFraction) * size.width
        drawLine(grid, Offset(x, 0f), Offset(x, size.height))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0 kgf · ${thrustNumber(model.velocitiesKmh.first())} km/h")
        Text("${thrustNumber(model.velocitiesKmh.last())} km/h")
    }
    if (model.velocitiesKmh.size > 1) Slider(speedFraction, { speedFraction = it },
        modifier = Modifier.testTag("jet-curve-speed").semantics { contentDescription = "查看推力的速度" })
    Text("速度 ${thrustNumber(speed)} km/h · 军用 ${thrustNumber(model.thrust(altitude, speed))} · 加力 ${thrustNumber(model.thrust(altitude, speed, true))} kgf/台")
    if ((military + afterburner.orEmpty()).any { it == null }) Text("部分网格点缺失，曲线在缺失处断开。")
}

private fun thrustNumber(value: Double?) = value?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "—"
