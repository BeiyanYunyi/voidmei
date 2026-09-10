package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import voidmei.telemetry.*
import java.util.Locale

@Composable
internal fun AoaMarginPanel(telemetry: Telemetry, model: AircraftAlertModel?, warningPercent: Double = 25.0) {
    val margin = PositiveAoaMargin.fromTelemetry(telemetry, model)
    val warning = margin != null && (margin.degrees <= 0 || margin.fraction < warningPercent / 100.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("距模型正迎角限 " + (margin?.let { String.format(Locale.ROOT, "%.1f°", it.degrees) } ?: "—"),
            style = MaterialTheme.typography.bodySmall)
        if (warning) Text(if (margin!!.degrees <= 0) "已达模型正迎角限" else "正迎角余量低于阈值",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (margin != null) LinearProgressIndicator(
            progress = { margin.fraction.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().testTag("aoa-margin-bar"),
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
