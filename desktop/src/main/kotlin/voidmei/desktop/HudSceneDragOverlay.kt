package voidmei.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import voidmei.config.HudSceneLayout
import kotlin.math.roundToInt

/** Preview-only input layer. Transparent/empty regions remain selectable by their outlines. */
@Composable
internal fun HudSceneDragOverlay(layout: HudSceneLayout, onMove: (String, Int, Int) -> Unit,
    onResize: ((String, Int, Int) -> Unit)? = null) {
    val current by rememberUpdatedState(layout)
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    val density = LocalDensity.current.density
    var selected by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / layout.width, maxHeight.value / layout.height,
            if (layout.displayId == null) 1f else Float.MAX_VALUE).coerceAtLeast(.01f)
        val pixelsPerUnit = density * scale
        Box(Modifier.fillMaxSize().testTag("hud-scene-drag-overlay")
            .pointerInput(layout.width, layout.height, pixelsPerUnit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val point = down.position
                    val px = point.x / pixelsPerUnit
                    val py = point.y / pixelsPerUnit
                    val hit = current.regions.lastOrNull { px >= it.x && px < it.x + it.width && py >= it.y && py < it.y + it.height }
                    selected = hit?.id
                    val resizing = hit != null && resize != null &&
                        px >= hit.x + hit.width - minOf(hit.width.toFloat(), 12f / scale) &&
                        py >= hit.y + hit.height - minOf(hit.height.toFloat(), 12f / scale)
                    var x = (if (resizing) hit?.width else hit?.x)?.toFloat() ?: 0f
                    var y = (if (resizing) hit?.height else hit?.y)?.toFloat() ?: 0f
                    var previous = point
                    down.consume()
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val amount = change.position - previous
                        previous = change.position
                        change.consume()
                        current.regions.firstOrNull { it.id == hit?.id }?.let { region ->
                            if (resizing) {
                                x = (x + amount.x / pixelsPerUnit).coerceIn(80f, (current.width - region.x).toFloat())
                                y = (y + amount.y / pixelsPerUnit).coerceIn(40f, (current.height - region.y).toFloat())
                                if (x.roundToInt() != region.width || y.roundToInt() != region.height)
                                    resize?.invoke(region.id, x.roundToInt(), y.roundToInt())
                            } else {
                                x = (x + amount.x / pixelsPerUnit).coerceIn(0f, (current.width - region.width).toFloat())
                                y = (y + amount.y / pixelsPerUnit).coerceIn(0f, (current.height - region.height).toFloat())
                                if (x.roundToInt() != region.x || y.roundToInt() != region.y)
                                    move(region.id, x.roundToInt(), y.roundToInt())
                            }
                        }
                    } while (change.pressed)
                }
            }) {
            layout.regions.forEach { region -> key(region.id) {
                Box(Modifier.offset((region.x * scale).dp, (region.y * scale).dp)
                    .size((region.width * scale).dp, (region.height * scale).dp)
                    .border(1.dp, if (selected == region.id) Color.Yellow else Color.Cyan)
                    .testTag("hud-drag-region-${region.id}")) {
                    Text(region.content.label, color = Color.Cyan)
                    if (onResize != null) Box(Modifier.align(Alignment.BottomEnd)
                        .size(minOf(12f, region.width * scale).dp, minOf(12f, region.height * scale).dp)
                        .background(Color.Cyan).testTag("hud-resize-region-${region.id}"))
                }
            } }
        }
    }
}
