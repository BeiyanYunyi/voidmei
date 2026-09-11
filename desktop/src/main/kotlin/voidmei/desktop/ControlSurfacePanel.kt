package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import voidmei.telemetry.Telemetry
import voidmei.telemetry.controlSurfacePercent

@Composable
internal fun ControlSurfacePanel(telemetry: Telemetry, fields: List<String>? = null, showStick: Boolean = false) {
    val axes = listOf(Triple("aileron", "副翼", telemetry.aileronPercent), Triple("elevator", "升降舵", telemetry.elevatorPercent),
        Triple("rudder", "方向舵", telemetry.rudderPercent),
        Triple("wing_sweep", "后掠", telemetry.wingSweepRatio?.takeIf { it in 0.0..1.0 }?.times(100)))
    val selected = fields?.distinct()?.mapNotNull { id -> axes.find { it.first == id } } ?: axes.take(3)
    val stick = showStick && selected.any { it.first == "aileron" } && selected.any { it.first == "elevator" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (selected.isEmpty()) Text("未选择操纵面")
        BoxWithConstraints {
            if (stick && maxWidth >= 360.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(140.dp)) { ControlStickPanel(telemetry) }
                Column(Modifier.weight(1f)) { ControlAxisReadings(selected) }
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stick) ControlStickPanel(telemetry)
                ControlAxisReadings(selected)
            }
        }
        if (selected.isNotEmpty()) Text(if (selected.any { it.first == "wing_sweep" })
            "舵面刻度 −100% 至 +100%；后掠刻度 0% 至 100%。百分比不代表实际角度。"
            else "刻度 −100% / 0 / +100%；百分比不代表实际偏转角。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ControlAxisReadings(selected: List<Triple<String, String, Double?>>) {
    val colors = LocalReadingColors.current
    val track = colors.label ?: Color(0xFF9EB1C0)
    val marker = colors.value ?: Color(0xFF84DEC6)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        selected.forEach { (id, label, raw) ->
            val minimum = if (id == "wing_sweep") 0 else -100
            val value = if (id == "wing_sweep") raw?.takeIf { it in 0.0..100.0 } else controlSurfacePercent(raw)
            Text(buildAnnotatedString {
                append(label)
                colors.label?.let { addStyle(SpanStyle(color = it), 0, length) }
                append(" ")
                val numberStart = length
                append(readingNumber(value, 1))
                colors.value?.let { addStyle(SpanStyle(color = it), numberStart, length) }
                append("%")
                (colors.unit ?: colors.value)?.let { addStyle(SpanStyle(color = it), length - 1, length) }
            }, Modifier.testTag("hud-control-reading-$id"))
            Canvas(Modifier.fillMaxWidth().height(24.dp).testTag("hud-control-$id").semantics {
                contentDescription = if (id == "wing_sweep") "$label，刻度 0% 至 100%" else "$label，刻度 -100% 至 +100%"
                stateDescription = value?.let { "${readingNumber(it, 1)}%" } ?: "数据不可用"
            }) {
                val inset = minOf(6.dp.toPx(), size.width / 2)
                val y = size.height / 2
                drawLine(track, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                drawLine(track, Offset(size.width / 2, y - 6.dp.toPx()), Offset(size.width / 2, y + 6.dp.toPx()), 1.dp.toPx())
                value?.let {
                    val x = inset + ((it - minimum) / (100 - minimum)).toFloat() * (size.width - 2 * inset)
                    drawCircle(marker, 4.dp.toPx().coerceAtMost(size.minDimension / 2), Offset(x, y))
                }
            }
        }
    }
}
