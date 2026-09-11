package voidmei.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import voidmei.telemetry.MapConnection

@Composable
internal fun HudMapObjects(endpoint: String?, shared: StateFlow<MapConnection>?, height: Int) {
    if (endpoint == null && shared == null) { Text("地图数据不可用"); return }
    key(endpoint, shared) {
        val flow = shared ?: remember(endpoint) { mapStates(requireNotNull(endpoint)) }
        val state by flow.collectAsState(MapConnection.Connecting)
        when (val current = state) {
            MapConnection.Connecting -> Text("正在连接地图…")
            MapConnection.Waiting -> Text("等待有效飞行地图")
            is MapConnection.Unavailable -> Text("地图不可用：${current.reason}")
            is MapConnection.Available -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                val side = minOf(maxWidth, (height - 160).coerceAtLeast(80).dp)
                Column {
                    MapObjectPlot(current.snapshot, interactive = false, side = side)
                }
            }
        }
    }
}
