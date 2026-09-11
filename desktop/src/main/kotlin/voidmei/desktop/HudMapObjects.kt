package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.ImageBitmap
import voidmei.telemetry.MapConnection

@Composable
internal fun HudMapObjects(endpoint: String?, shared: StateFlow<MapConnection>?, title: String) {
    val labelColor = LocalReadingColors.current.label ?: LocalContentColor.current
    Column(Modifier.fillMaxSize().padding(end = 8.dp)) {
    if (title.isNotBlank()) HudOverlayText(title, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (endpoint == null && shared == null) { HudOverlayText("地图数据不可用", color = labelColor); return@Column }
    key(endpoint, shared) {
        val cache = LocalHudMapBackgroundCache.current ?: remember(endpoint) { endpoint?.let(::HudMapBackgroundCache) }
        val flow = shared ?: remember(endpoint) { mapStates(requireNotNull(endpoint)) }
        val state by flow.collectAsState(MapConnection.Connecting)
        when (val current = state) {
            MapConnection.Connecting -> HudOverlayText("正在连接地图…", color = labelColor)
            MapConnection.Waiting -> HudOverlayText("等待有效飞行地图", color = labelColor)
            is MapConnection.Unavailable -> HudOverlayText("地图不可用：${current.reason}", color = labelColor)
            is MapConnection.Available -> {
                val bounds = current.snapshot.bounds
                var background by remember(endpoint, bounds) { mutableStateOf<ImageBitmap?>(null) }
                var error by remember(endpoint, bounds) { mutableStateOf<String?>(null) }
                LaunchedEffect(endpoint, bounds) {
                    if (endpoint == null) return@LaunchedEffect
                    do {
                        try {
                            background = requireNotNull(cache).load(bounds)
                            error = null
                            break
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "底图加载失败" }
                        // A click-through HUD cannot expose a usable retry button.
                        if (bounds.generation == null) break
                        delay(5000)
                    } while (isActive)
                }
                val backgroundStatus = if (endpoint != null && background == null)
                    error?.let { "底图不可用：$it" } ?: "底图加载中…" else null
                MapObjectPlot(current.snapshot, background, interactive = false, compact = true,
                    plotModifier = Modifier.fillMaxWidth().weight(1f), backgroundStatus = backgroundStatus)
            }
        }
    }
    }
}
