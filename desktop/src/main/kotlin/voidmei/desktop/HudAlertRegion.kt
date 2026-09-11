package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voidmei.telemetry.FlightAlert

/** Keep the region title visible while the alert list scrolls within the remaining height. */
@Composable
internal fun HudAlertRegion(title: String, alerts: List<FlightAlert>) {
    Column(Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotBlank()) Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            FlightAlertPanel(alerts, compact = true, maximumHeight = maxHeight)
        }
    }
}
