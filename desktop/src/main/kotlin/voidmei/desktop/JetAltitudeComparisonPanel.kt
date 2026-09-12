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
import java.util.Locale
import voidmei.fm.JetThrustModel

@Composable
internal fun JetAltitudeComparisonPanel(model: JetThrustModel, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable(model) { mutableStateOf(initiallyExpanded) }
    var page by rememberSaveable(model) { mutableStateOf(0) }
    var afterburner by rememberSaveable(model) { mutableStateOf(model.afterburnerKgf != null) }
    var speedFraction by rememberSaveable(model) { mutableStateOf(0f) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起多高度推力对比" else "展开多高度推力对比") }
    if (!expanded) return
    val indices = (page * 6 until minOf(page * 6 + 6, model.altitudesM.size)).toList()
    val table = if (afterburner) model.afterburnerKgf else model.militaryKgf
    val ceiling = indices.flatMap { table?.get(it).orEmpty() }.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val speed = model.velocitiesKmh.first() * (1 - speedFraction.toDouble()) + model.velocitiesKmh.last() * speedFraction.toDouble()
    val colors = listOf(Color(0xFF84DEC6), Color(0xFFFFBB66), Color(0xFF9AAFFF), Color(0xFFFF8DA1), Color(0xFFC3E478), Color(0xFFDCA0F0))
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = .35f)
    fun number(value: Double?) = value?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
    Text("多高度推力–真空速 · ${model.source} · kgf/台")
    Row {
        FilterChip(!afterburner, { afterburner = false }, label = { Text("多高度军用") })
        FilterChip(afterburner, { afterburner = true }, label = { Text("多高度加力") })
    }
    if (model.altitudesM.size > 6) Row {
        TextButton(enabled = page > 0, onClick = { page-- }, modifier = Modifier.testTag("jet-altitudes-previous")) { Text("上一组高度") }
        Text("${page + 1} / ${(model.altitudesM.size + 5) / 6}")
        TextButton(enabled = (page + 1) * 6 < model.altitudesM.size, onClick = { page++ }, modifier = Modifier.testTag("jet-altitudes-next")) { Text("下一组高度") }
    }
    Text("纵轴 0–${number(ceiling)} kgf/台 · 横轴 ${number(model.velocitiesKmh.first())}–${number(model.velocitiesKmh.last())} km/h TAS")
    Canvas(Modifier.fillMaxWidth().height(240.dp).testTag("jet-altitudes-plot")
        .chartFractionPicker { speedFraction = it }
        .semantics { contentDescription = "多高度推力曲线；每种颜色对应一个高度，缺失点断开" }) {
        repeat(5) { i -> drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
        indices.forEachIndexed { colorIndex, row ->
            val path = Path()
            var connected = false
            model.velocitiesKmh.forEachIndexed cell@ { column, velocity ->
                val value = table?.get(row)?.get(column)
                if (value == null) { connected = false; return@cell }
                val fraction = if (model.velocitiesKmh.size == 1) .5 else (velocity - model.velocitiesKmh.first()) / (model.velocitiesKmh.last() - model.velocitiesKmh.first())
                val point = Offset(fraction.toFloat() * size.width, (1 - value / ceiling).toFloat() * size.height)
                if (connected) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                connected = true
                drawCircle(colors[colorIndex], 2.dp.toPx(), point)
            }
            drawPath(path, colors[colorIndex], style = Stroke(2.dp.toPx()))
        }
        val x = (if (model.velocitiesKmh.size == 1) .5f else speedFraction) * size.width
        drawLine(grid, Offset(x, 0f), Offset(x, size.height))
    }
    if (model.velocitiesKmh.size > 1) Slider(speedFraction, { speedFraction = it }, modifier = Modifier.testTag("jet-altitudes-speed"))
    Text("探针 TAS ${number(speed)} km/h")
    indices.forEachIndexed { index, row ->
        Text("高度 ${number(model.altitudesM[row])} m：${number(model.thrust(model.altitudesM[row], speed, afterburner))} kgf/台",
            color = colors[index])
    }
    if (table == null) Text("模型未提供加力推力表，不使用军用数据替代。")
    else if (indices.any { row -> table[row].any { it == null } }) Text("存在缺失网格点；曲线断开，相关速度的推力保持未知。")
    Text("每组最多六个高度，直接使用 FM 高度节点；各组纵轴独立缩放。表内速度插值，不外推，不合并多台发动机。", style = MaterialTheme.typography.bodySmall)
}
