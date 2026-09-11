package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import voidmei.telemetry.Telemetry
import voidmei.telemetry.controlSurfacePercent

/** Same screen axes as the legacy locator: positive aileron right, positive elevator down. */
@Composable
internal fun ControlStickPanel(telemetry: Telemetry) {
    val aileron = controlSurfacePercent(telemetry.aileronPercent)
    val elevator = controlSurfacePercent(telemetry.elevatorPercent)
    val label = "副翼：右为正；升降舵：下为正"
    val track = LocalReadingColors.current.label ?: Color(0xFF9EB1C0)
    val marker = LocalReadingColors.current.value ?: Color(0xFF84DEC6)
    Text(label, style = MaterialTheme.typography.bodySmall, color = track)
    Canvas(Modifier.size(140.dp).testTag("hud-control-stick").semantics {
        contentDescription = "二维操纵面，$label，刻度 −100% 至 +100%"
        stateDescription = if (aileron != null && elevator != null)
            "副翼 ${readingNumber(aileron, 1)}%，升降舵 ${readingNumber(elevator, 1)}%" else "数据不可用"
    }) {
        val inset = minOf(8.dp.toPx(), size.minDimension / 2)
        val extent = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
        drawRect(track, Offset(inset, inset), extent, style = Stroke(1.dp.toPx()))
        drawLine(track, Offset(size.width / 2, inset), Offset(size.width / 2, size.height - inset), 1.dp.toPx())
        drawLine(track, Offset(inset, size.height / 2), Offset(size.width - inset, size.height / 2), 1.dp.toPx())
        if (aileron != null && elevator != null) {
            val x = inset + ((aileron + 100) / 200).toFloat() * extent.width
            val y = inset + ((elevator + 100) / 200).toFloat() * extent.height
            val radius = minOf(5.dp.toPx(), inset)
            drawLine(marker, Offset(x - radius, y), Offset(x + radius, y), 3.dp.toPx())
            drawLine(marker, Offset(x, y - radius), Offset(x, y + radius), 3.dp.toPx())
        }
    }
}
