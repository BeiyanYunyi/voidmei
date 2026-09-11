package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOn
import voidmei.telemetry.*

@Composable
internal fun MapPanel(endpoint: String, flying: Boolean, shared: kotlinx.coroutines.flow.StateFlow<MapConnection>? = null) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起地图对象" else "查看地图对象") }
    if (!expanded) return
    val state = if (!flying) MapConnection.Waiting else key(endpoint, shared) {
        val flow = shared ?: remember(endpoint) { mapStates(endpoint) }
        flow.collectAsState(MapConnection.Connecting).value
    }
    when (val current = state) {
        MapConnection.Connecting -> Text("正在连接地图…")
        MapConnection.Waiting -> Text("等待有效飞行地图")
        is MapConnection.Unavailable -> Text("地图不可用：${current.reason}", color = MaterialTheme.colorScheme.error)
        is MapConnection.Available -> {
            val bounds = current.snapshot.bounds
            var background by remember(endpoint, bounds) { mutableStateOf<ImageBitmap?>(null) }
            var error by remember(endpoint, bounds) { mutableStateOf<String?>(null) }
            var retry by remember(endpoint, bounds) { mutableStateOf(0) }
            LaunchedEffect(endpoint, bounds, retry) {
                error = null
                try {
                    background = withContext(Dispatchers.IO) {
                        HttpTelemetryTransport(endpoint).use { loadMapBackground(it, bounds).toComposeImageBitmap() }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "底图加载失败" }
            }
            MapObjectPlot(current.snapshot, background)
            if (background == null) {
                Text(error?.let { "底图不可用：$it" } ?: "正在加载地图底图…", style = MaterialTheme.typography.bodySmall)
                if (error != null) TextButton(onClick = { retry++ }) { Text("重试底图") }
            }
        }
    }
}

private data class MapSelection(val obj: MapObject, val distanceM: Double?)

@Composable
internal fun MapObjectPlot(snapshot: MapSnapshot, background: ImageBitmap? = null,
    interactive: Boolean = true, side: androidx.compose.ui.unit.Dp = 320.dp) {
    val distanceScale = MapScale.fromBounds(snapshot.bounds)
    var selection by remember(snapshot.bounds) { mutableStateOf<MapSelection?>(null) }
    val currentSnapshot by rememberUpdatedState(snapshot)
    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    Text("地图对象示意 · ${snapshot.objects.size} 个对象 · 每秒更新" + if (interactive) " · 点击点状对象查看详情" else "")
    val gridLines = remember(snapshot.bounds) { MapGrid.lines(snapshot.bounds) }
    Text("玩家格号：${MapGrid.playerCell(snapshot) ?: "—"}", style = MaterialTheme.typography.bodySmall)
    if (gridLines == null) Text("地图网格不可用", style = MaterialTheme.typography.bodySmall)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Canvas(Modifier.size(side).onSizeChanged { plotSize = it }.clipToBounds().pointerInput(snapshot.bounds, interactive) {
        if (!interactive) return@pointerInput
        detectTapGestures { tap ->
            val current = currentSnapshot
            val viewport = MapViewport.fit(current.bounds, size.width.toDouble(), size.height.toDouble())
            val obj = viewport?.let { MapHitTest.nearest(current, it, MapPoint(tap.x.toDouble(), tap.y.toDouble()), 12.dp.toPx().toDouble()) }
            selection = obj?.let { MapSelection(it, current.distanceFromPlayerM(it)) }
        }
    }.testTag("map-objects-plot").semantics { contentDescription = if (background == null) "地图对象位置与方向示意，不含底图" else "地图底图与对象位置方向" }) {
        val viewport = MapViewport.fit(snapshot.bounds, size.width.toDouble(), size.height.toDouble()) ?: return@Canvas
        fun project(point: MapPoint): Offset {
            val projected = viewport.project(point)
            return Offset(projected.x.toFloat(), projected.y.toFloat())
        }
        fun visible(point: MapPoint) = point.x in 0.0..1.0 && point.y in 0.0..1.0
        clipRect(viewport.left.toFloat(), viewport.top.toFloat(),
            (viewport.left + viewport.width).toFloat(), (viewport.top + viewport.height).toFloat()) {
        background?.let { drawImage(it,
            dstOffset = IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()),
            dstSize = IntSize(viewport.width.roundToInt().coerceAtLeast(1), viewport.height.roundToInt().coerceAtLeast(1))) }
        gridLines?.vertical?.forEach { fraction ->
            drawLine(grid, project(MapPoint(fraction, 0.0)), project(MapPoint(fraction, 1.0)))
        }
        gridLines?.horizontal?.forEach { fraction ->
            drawLine(grid, project(MapPoint(0.0, fraction)), project(MapPoint(1.0, fraction)))
        }
        snapshot.objects.forEach { obj ->
            val color = obj.colorRgb?.let { Color(0xFF000000L or it.toLong()) } ?: Color.LightGray
            val start = obj.start; val end = obj.end
            if (start != null && end != null) MapSegment(start, end).clipped()?.let { segment ->
                drawLine(color, project(segment.start), project(segment.end), 2.dp.toPx())
            }
            obj.position?.takeIf(::visible)?.let { point ->
                val player = obj.icon.equals("Player", true)
                val marker = if (player) Color.Yellow else color
                val center = project(point)
                obj.unitDirection?.let { direction ->
                    val end = center + Offset(direction.x.toFloat(), direction.y.toFloat()) * 20.dp.toPx()
                    drawLine(marker, center, end, 2.dp.toPx())
                }
                drawCircle(marker, (if (player) 5 else 3).dp.toPx(), center)
            }
        }
            selection?.takeIf { interactive }?.obj?.position?.let { point ->
                drawCircle(Color.White, 8.dp.toPx(), project(point), style = Stroke(2.dp.toPx()))
            }
        }
    }
    selection?.takeIf { interactive }?.let { selected ->
        Text("点击时对象：${selected.obj.type ?: "类型未知"} · ${selected.obj.icon ?: "图标未知"}")
        Text("点击时到玩家的平面距离：${selected.distanceM?.let { String.format(java.util.Locale.ROOT, "%.0f m", it) } ?: "未知"}")
        Text("白圈保留点击时位置；对象没有稳定编号，不自动跟踪。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { selection = null }) { Text("清除对象选择") }
    }
    distanceScale?.let { scale ->
        val distance = if (scale.metres >= 1000) "${(scale.metres / 1000).toString().removeSuffix(".0")} km"
            else "${scale.metres.toString().removeSuffix(".0")} m"
        Text("距离标尺：$distance", style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.size(side, 16.dp).testTag("map-distance-scale").semantics { contentDescription = "距离标尺 $distance" }) {
            val viewport = MapViewport.fit(snapshot.bounds, plotSize.width.toDouble(), plotSize.height.toDouble()) ?: return@Canvas
            val length = (viewport.width * scale.widthFraction).toFloat()
            val y = size.height / 2
            val x = viewport.left.toFloat()
            drawLine(Color.White, Offset(x, y), Offset(x + length, y), 2.dp.toPx())
            drawLine(Color.White, Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 2.dp.toPx())
            drawLine(Color.White, Offset(x + length, y - 4.dp.toPx()), Offset(x + length, y + 4.dp.toPx()), 2.dp.toPx())
        }
    }
    Text((if (background == null) "归一化坐标示意，不含地图底图。" else "底图与对象按归一化坐标叠加。") + "按地图宽高等比例显示；短线表示方向，范围外点状对象不绘制，线状对象裁剪到地图边界。", style = MaterialTheme.typography.bodySmall)
    snapshot.player?.position?.let { Text("玩家位置 ${"%.3f".format(java.util.Locale.ROOT, it.x)}, ${"%.3f".format(java.util.Locale.ROOT, it.y)}") }
        ?: Text("玩家位置未知")
}
