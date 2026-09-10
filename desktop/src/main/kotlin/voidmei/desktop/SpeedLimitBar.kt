package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import voidmei.telemetry.*
import java.util.Locale

@Composable
internal fun SpeedLimitBar(flight: ConnectionState.Flying, model: AircraftAlertModel?) {
    val scale = SpeedLimitScale.fromFlight(flight, model) ?: return
    val markers = listOf(
        Triple("基础质量失速", scale.stallRatio, Color(0xFFFF6577)),
        Triple("副翼舵效衰减", scale.aileronRatio, Color(0xFFFFD580)),
        Triple("方向舵舵效衰减", scale.rudderRatio, Color(0xFF84DEC6)),
        Triple("Mach 1", scale.machOneRatio, Color(0xFFA6B9FF)),
    ).filter { it.second != null && it.second!! in 0.0..1.0 }
    val fill = if (scale.ratio >= 0.95) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant
    val description = "${if (scale.limitingMach) "Mach" else "IAS"} 限制主导" +
        markers.joinToString(prefix = if (markers.isEmpty()) "" else "；", separator = "；") {
            "${it.first} ${String.format(Locale.ROOT, "%.1f", it.second!! * 100)}%"
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (scale.limitingMach) "Mach 限制主导" else "IAS 限制主导", style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(24.dp).testTag("speed-limit-bar").semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(scale.ratio.coerceIn(0.0, 1.0).toFloat(), 0f..1f)
        }) {
            val top = 6.dp.toPx()
            val height = 12.dp.toPx()
            drawRect(background, Offset(0f, top), Size(size.width, height))
            drawRect(fill, Offset(0f, top), Size(size.width * scale.ratio.coerceIn(0.0, 1.0).toFloat(), height))
            markers.forEach { (_, ratio, color) ->
                val x = (size.width * ratio!!.toFloat()).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                drawLine(color, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        markers.forEach { (label, ratio, color) ->
            Text("$label ${String.format(Locale.ROOT, "%.1f", ratio!! * 100)}%", color = color,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
