package voidmei.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import voidmei.telemetry.AlertSeverity
import voidmei.telemetry.FlightAlert

internal const val HUD_ALERT_HEIGHT_DP = 112

@Composable
internal fun FlightAlertPanel(alerts: List<FlightAlert>, compact: Boolean = false, maximumHeight: Dp = HUD_ALERT_HEIGHT_DP.dp) {
    if (alerts.isEmpty()) return
    val ordered = alerts.distinct().sortedBy { it.severity }
    val scroll = key(ordered) { rememberScrollState() }
    Box(Modifier.fillMaxWidth().testTag("flight-alerts")
        // The containing layout owns the height budget: the vertical HUD caps it, a dedicated region need not.
        .then(if (compact) Modifier.heightIn(max = maximumHeight.coerceAtLeast(0.dp)) else Modifier)) {
        Column(Modifier.fillMaxWidth()
            .then(if (compact) Modifier.verticalScroll(scroll).padding(end = 14.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.forEach { alert ->
                val warning = alert.severity == AlertSeverity.WARNING
                Text("${if (warning) "警告" else "提示"} · ${alert.label}",
                    modifier = Modifier.testTag("flight-alert-${alert.name}"),
                    color = if (warning) LocalReadingColors.current.warning ?: Color(0xFFFF967B) else Color(0xFFFFD580),
                    fontSize = if (compact) 13.sp else 16.sp)
            }
        }
        if (compact && scroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag("flight-alert-scrollbar"))
        if (compact && LocalHudLayoutInspection.current && scroll.maxValue in 1 until Int.MAX_VALUE)
            Text("告警超出区域", Modifier.align(Alignment.BottomEnd).testTag("flight-alert-overflow")
                .background(Color(0xFF713E00)).padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}
