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
import java.util.Locale

@Composable
internal fun JetComparisonPanel(baseline: NamedModel, current: NamedModel?) {
    var expanded by remember { mutableStateOf(false) }
    var leftSource by remember { mutableStateOf<String?>(null) }
    var rightSource by remember { mutableStateOf<String?>(null) }
    var afterburner by remember { mutableStateOf(false) }
    var selectedHeight by remember { mutableStateOf<Double?>(null) }
    var selectedSpeed by remember { mutableStateOf<Double?>(null) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起推力叠加比较" else "展开推力叠加比较") }
    if (!expanded) return
    if (current == null) { Text("等待当前模型，推力比较条件已保留。"); return }
    val left = baseline.jets.firstOrNull { it.source == leftSource } ?: baseline.jets.firstOrNull()
    val right = current.jets.firstOrNull { it.source == rightSource } ?: current.jets.firstOrNull()
    Text("基准 ${baseline.aircraft}（青色） · 当前 ${current.aircraft}（橙色） · kgf/台")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        baseline.jets.forEach { jet ->
            FilterChip(left == jet, { leftSource = jet.source }, label = { Text("基准推力表 ${jet.source}") })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        current.jets.forEach { jet ->
            FilterChip(right == jet, { rightSource = jet.source }, label = { Text("当前推力表 ${jet.source}") })
        }
    }
    if (left == null && right == null) { Text("两侧均无可用喷气推力表"); return }
    val models = listOfNotNull(left, right)
    val minimumHeight = models.minOf { it.altitudesM.first() }
    val maximumHeight = models.maxOf { it.altitudesM.last() }
    val speeds = remember(left, right) { models.flatMap { it.velocitiesKmh }.distinct().sorted() }
    val minimumSpeed = speeds.first()
    val maximumSpeed = speeds.last()
    val altitude = (selectedHeight ?: minimumHeight).coerceIn(minimumHeight, maximumHeight)
    val speed = (selectedSpeed ?: minimumSpeed).coerceIn(minimumSpeed, maximumSpeed)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!afterburner, { afterburner = false }, label = { Text("军用推力比较") })
        FilterChip(afterburner, { afterburner = true }, label = { Text("加力推力比较") })
    }
    fun Double?.number() = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
    Text("共同高度 ${altitude.number()} m")
    if (maximumHeight > minimumHeight) Slider(((altitude - minimumHeight) / (maximumHeight - minimumHeight)).toFloat(),
        { selectedHeight = minimumHeight * (1 - it) + maximumHeight * it },
        modifier = Modifier.semantics { contentDescription = "推力比较高度" })
    val leftValues = remember(left, speeds, altitude, afterburner) { speeds.map { left?.thrust(altitude, it, afterburner) } }
    val rightValues = remember(right, speeds, altitude, afterburner) { speeds.map { right?.thrust(altitude, it, afterburner) } }
    val ceiling = (leftValues + rightValues).filterNotNull().maxOrNull()?.coerceAtLeast(1.0)
    if (leftValues.all { it == null }) Text("基准在此高度/模式无有效推力数据")
    if (rightValues.all { it == null }) Text("当前在此高度/模式无有效推力数据")
    if (ceiling != null) {
        val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        Text("点击图表查看对应速度；也可使用下方滑块。", style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(240.dp).testTag("jet-comparison-plot")
            .chartFractionPicker { selectedSpeed = minimumSpeed * (1 - it.toDouble()) + maximumSpeed * it.toDouble() }
            .semantics { contentDescription = "共同高度下的双机型速度推力比较；范围外和缺失处断开" }) {
            repeat(5) { i -> val y = size.height * i / 4; drawLine(grid, Offset(0f, y), Offset(size.width, y)) }
            fun x(value: Double) = if (maximumSpeed == minimumSpeed) size.width / 2 else
                ((value - minimumSpeed) / (maximumSpeed - minimumSpeed) * size.width).toFloat()
            fun series(values: List<Double?>, color: Color) {
                val path = Path()
                var connected = false
                values.forEachIndexed { index, value ->
                    if (value == null) connected = false else {
                        val point = Offset(x(speeds[index]), ((1 - value / ceiling) * size.height).toFloat())
                        if (connected) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                        drawCircle(color, 2.dp.toPx(), point)
                        connected = true
                    }
                }
                drawPath(path, color, style = Stroke(2.dp.toPx()))
            }
            series(leftValues, Color(0xFF84DEC6)); series(rightValues, Color(0xFFFFBB66))
            drawLine(grid, Offset(x(speed), 0f), Offset(x(speed), size.height))
        }
        Text("纵轴 0–${ceiling.number()} kgf/台 · 横轴 ${minimumSpeed.number()}–${maximumSpeed.number()} km/h")
    }
    if (maximumSpeed > minimumSpeed) Slider(((speed - minimumSpeed) / (maximumSpeed - minimumSpeed)).toFloat(),
        { selectedSpeed = minimumSpeed * (1 - it) + maximumSpeed * it },
        modifier = Modifier.semantics { contentDescription = "推力比较速度" })
    Text("TAS ${speed.number()} km/h · 基准 ${left?.thrust(altitude, speed, afterburner).number()} kgf · 当前 ${right?.thrust(altitude, speed, afterburner).number()} kgf")
    Text("分别在各自表内插值，范围外或缺失点保持未知；未追加燃油改装修正，不合计整机推力。", style = MaterialTheme.typography.bodySmall)
}
