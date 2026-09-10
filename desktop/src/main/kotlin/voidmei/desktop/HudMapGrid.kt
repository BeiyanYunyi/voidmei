package voidmei.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow
import voidmei.telemetry.*

@Composable
internal fun HudMapGrid(endpoint: String, shared: StateFlow<MapConnection>? = null) {
    key(endpoint, shared) {
        val flow = shared ?: remember(endpoint) { mapStates(endpoint) }
        val state by flow.collectAsState(MapConnection.Connecting)
        MapGridReading(state)
    }
}

@Composable
internal fun MapGridReading(state: MapConnection) {
    val grid = (state as? MapConnection.Available)?.snapshot?.let(MapGrid::playerCell)
    Text("地图格号：${grid ?: "—"}")
}
