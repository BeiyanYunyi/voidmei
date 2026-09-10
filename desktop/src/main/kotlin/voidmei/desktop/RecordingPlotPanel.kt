package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import voidmei.recording.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun RecordingPlotPanel(summary: FlightRecordSummary, plots: Map<String, List<RecordedPlotPoint>>,
    fields: List<Pair<String, String>> = RecordedField.entries.map { it.id to it.label }, timeOffsetMs: Long = 0, replayFrame: RecordedFrame? = null, onPointSelected: ((RecordedPlotPoint) -> Unit)? = null, onRangeSelected: ((Long, Long) -> Unit)? = null) {
    var selected by remember(plots) { mutableStateOf(fields.firstOrNull { !plots[it.first].isNullOrEmpty() }?.first ?: fields.first().first) }
    Text("记录时间曲线")
    fields.take(RecordedField.quick.size).chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (key, label) -> FilterChip(selected == key, { selected = key }, label = { Text(label) }) }
        }
    }
    var moreFields by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { moreFields = true }) { Text("更多记录字段") }
        DropdownMenu(expanded = moreFields, onDismissRequest = { moreFields = false }) {
            fields.drop(RecordedField.quick.size).forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { selected = key; moreFields = false })
            }
        }
    }
    Text("已选字段：${fields.first { it.first == selected }.second}")
    if (selected in setOf("heading_deg", "roll_deg"))
        Text("保留原始角度；相邻角度跳变超过 180° 时断线，避免跨回绕边界连线。", style = MaterialTheme.typography.bodySmall)
    replayFrame?.let { frame ->
        val value = frame.values[selected]
        val outside = frame.elapsedMs !in timeOffsetMs..(timeOffsetMs + summary.spanMs)
        Text("同步原始帧 ${frame.sampleId} · ${String.format(Locale.ROOT, "%.3f", frame.elapsedMs / 1000.0)} s · " +
            "${value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "未知"} · ${fields.first { it.first == selected }.second}" +
            if (outside) "（当前曲线区间外）" else "", Modifier.testTag("recording-replay-value"))
    }
    val points = plots[selected].orEmpty()
    val range = summary.ranges[selected]
    if (points.isEmpty() || range == null) { Text("所选字段无有效记录"); return }
    var inspected by remember(selected, plots) { mutableStateOf(0) }
    val pointCallback by rememberUpdatedState(onPointSelected)
    fun inspect(index: Int) { inspected = index; pointCallback?.invoke(points[index]) }
    val inspectedPoint = points[inspected.coerceIn(points.indices)]
    val rangeCallback by rememberUpdatedState(onRangeSelected)
    var dragStart by remember(selected, plots) { mutableStateOf<Float?>(null) }
    var dragEnd by remember(selected, plots) { mutableStateOf(0f) }
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Text("${String.format(Locale.ROOT, "%.2f", range.maximum)} · ${fields.first { it.first == selected }.second}")
    Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("recording-plot")
        .semantics { contentDescription = "所选字段随记录时间的变化；缺失或长间隔处断开" }
        .pointerInput(points, summary.spanMs) {
            detectTapGestures { position ->
                if (size.width > 0) {
                    val time = (position.x / size.width).coerceIn(0f, 1f) * summary.spanMs.toDouble()
                    inspect(points.indices.minByOrNull { abs(points[it].elapsedMs.toDouble() - time) } ?: 0)
                }
            }
        }
        .pointerInput(points, summary.spanMs, timeOffsetMs) {
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    if (rangeCallback != null && summary.spanMs > 0 && size.width > 0) {
                        dragStart = position.x.coerceIn(0f, size.width.toFloat())
                        dragEnd = dragStart!!
                    }
                },
                onHorizontalDrag = { change, _ ->
                    if (dragStart != null) {
                        change.consume()
                        dragEnd = change.position.x.coerceIn(0f, size.width.toFloat())
                    }
                },
                onDragCancel = { dragStart = null },
                onDragEnd = {
                    val first = dragStart
                    dragStart = null
                    if (first != null && abs(first - dragEnd) >= 1f && size.width > 0) {
                        fun time(x: Float) = timeOffsetMs + (x.toDouble() / size.width * summary.spanMs).roundToLong()
                        rangeCallback?.invoke(time(minOf(first, dragEnd)), time(maxOf(first, dragEnd)))
                    }
                },
            )
        }) {
        repeat(5) { i -> drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
        val scale = maxOf(abs(range.minimum), abs(range.maximum), 1.0)
        val lower = range.minimum / scale
        val span = range.maximum / scale - lower
        val path = Path()
        fun position(point: RecordedPlotPoint): Offset {
            val x = if (summary.spanMs == 0L) size.width / 2 else (point.elapsedMs.toDouble() / summary.spanMs).toFloat() * size.width
            val y = if (span == 0.0) size.height / 2 else (1 - (point.value / scale - lower) / span).toFloat() * size.height
            return Offset(x, y)
        }
        points.forEach { point ->
            val location = position(point)
            if (point.connectFromPrevious) path.lineTo(location.x, location.y) else path.moveTo(location.x, location.y)
            drawCircle(Color(0xFF84DEC6), 1.5.dp.toPx(), location)
        }
        drawPath(path, Color(0xFF84DEC6), style = Stroke(2.dp.toPx()))
        val inspectedLocation = position(inspectedPoint)
        drawLine(Color(0xFFFFBB66), Offset(inspectedLocation.x, 0f), Offset(inspectedLocation.x, size.height), 1.dp.toPx())
        drawCircle(Color(0xFFFFBB66), 4.dp.toPx(), inspectedLocation)
        replayFrame?.takeIf { it.elapsedMs in timeOffsetMs..(timeOffsetMs + summary.spanMs) }?.let { frame ->
            val x = if (summary.spanMs == 0L) size.width / 2 else
                ((frame.elapsedMs - timeOffsetMs).toDouble() / summary.spanMs).toFloat() * size.width
            val color = Color(0xFF82B1FF)
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            frame.values[selected]?.takeIf { it.isFinite() }?.let { value ->
                val location = position(RecordedPlotPoint(frame.elapsedMs - timeOffsetMs, value, false))
                drawCircle(color, 5.dp.toPx(), location)
            }
        }
        dragStart?.let { first ->
            drawRect(Color(0xFF84DEC6).copy(alpha = .25f), Offset(minOf(first, dragEnd), 0f),
                Size(abs(first - dragEnd), size.height))
        }
    }
    Text("${String.format(Locale.ROOT, "%.2f", range.minimum)} · ${if (timeOffsetMs == 0L) "0" else (timeOffsetMs / 1000.0).toString()}–${(timeOffsetMs + summary.spanMs) / 1000.0} s")
    Text("保留采样点 ${inspected + 1}/${points.size} · ${String.format(Locale.ROOT, "%.3f", (timeOffsetMs + inspectedPoint.elapsedMs) / 1000.0)} s · " +
        "${String.format(Locale.ROOT, "%.4f", inspectedPoint.value)} · ${fields.first { it.first == selected }.second}", Modifier.testTag("recording-inspected-point"))
    if (points.size > 1) {
        Slider(value = inspected.toFloat(), onValueChange = { inspect(it.roundToInt().coerceIn(points.indices)) },
            valueRange = 0f..points.lastIndex.toFloat(), steps = (points.size - 2).coerceAtLeast(0),
            modifier = Modifier.testTag("recording-point-slider").semantics { contentDescription = "选择保留采样点" })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = inspected > 0, onClick = { inspect(inspected - 1) }) { Text("上一个采样点") }
            TextButton(enabled = inspected < points.lastIndex, onClick = { inspect(inspected + 1) }) { Text("下一个采样点") }
        }
    }
    if (onPointSelected != null) Text("选择保留点会按采样编号定位原始帧并暂停回看。", style = MaterialTheme.typography.bodySmall)
    if (onRangeSelected != null && summary.spanMs > 0) Text("在曲线上水平拖动选择时间区间，松开后重新分析；可从右向左拖选。", style = MaterialTheme.typography.bodySmall)
    Text("点击曲线选择时间最近的保留点，或逐点查看；读数来自保留的真实记录点，不在缺失区间插值。", style = MaterialTheme.typography.bodySmall)
    Text("按时间分桶保留首末值和极值；缺失或超过 2 秒的采样间隔不连线。含缺失数据的桶内仅显示点。", style = MaterialTheme.typography.bodySmall)
}
