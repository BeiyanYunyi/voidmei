package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import voidmei.telemetry.MapConnection

@Composable
internal fun HudMapObjects(endpoint: String?, shared: StateFlow<MapConnection>?, title: String) {
    Column(Modifier.fillMaxSize().padding(end = 8.dp)) {
    if (title.isNotBlank()) Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (endpoint == null && shared == null) { Text("地图数据不可用"); return@Column }
    key(endpoint, shared) {
        val flow = shared ?: remember(endpoint) { mapStates(requireNotNull(endpoint)) }
        val state by flow.collectAsState(MapConnection.Connecting)
        when (val current = state) {
            MapConnection.Connecting -> Text("正在连接地图…")
            MapConnection.Waiting -> Text("等待有效飞行地图")
            is MapConnection.Unavailable -> Text("地图不可用：${current.reason}")
            is MapConnection.Available -> {
                val bounds = current.snapshot.bounds
                var background by remember(endpoint, bounds) { mutableStateOf<ImageBitmap?>(null) }
                var error by remember(endpoint, bounds) { mutableStateOf<String?>(null) }
                LaunchedEffect(endpoint, bounds) {
                    if (endpoint == null) return@LaunchedEffect
                    do {
                        try {
                            background = withContext(Dispatchers.IO) {
                                HttpTelemetryTransport(endpoint).use { loadMapBackground(it, bounds).toComposeImageBitmap() }
                            }
                            error = null
                            break
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "底图加载失败" }
                        // A click-through HUD cannot expose a usable retry button.
                        if (bounds.generation == null) break
                        delay(5000)
                    } while (isActive)
                }
                if (endpoint != null && background == null) Text(
                    error?.let { "底图不可用：$it" } ?: "正在加载地图底图…",
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                MapObjectPlot(current.snapshot, background, interactive = false, compact = true,
                    plotModifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
    }
}
