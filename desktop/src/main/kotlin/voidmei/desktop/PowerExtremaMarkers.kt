package voidmei.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import voidmei.fm.PowerExtremum
import voidmei.fm.PowerExtremumKind

/** The triangle tip is the actual sample; its body points inward from a peak or valley. */
internal fun DrawScope.drawPowerExtrema(extrema: List<PowerExtremum>, ceiling: Double, color: Color) {
    if (!ceiling.isFinite() || ceiling <= 0) return
    clipRect {
        extrema.forEach { extremum ->
            val point = extremum.point
            if (!point.altitudeM.isFinite() || point.altitudeM !in 0.0..10000.0 ||
                !point.powerHp.isFinite() || point.powerHp !in 0.0..ceiling) return@forEach
            val x = (point.altitudeM / 10000 * size.width).toFloat()
            val y = ((1 - point.powerHp / ceiling) * size.height).toFloat()
            val halfWidth = 4.dp.toPx()
            if (extremum.kind == PowerExtremumKind.KINK) {
                drawPath(Path().apply {
                    moveTo(x, y - halfWidth)
                    lineTo(x + halfWidth, y)
                    lineTo(x, y + halfWidth)
                    lineTo(x - halfWidth, y)
                    close()
                }, color)
                return@forEach
            }
            val baseY = y + 8.dp.toPx() * if (extremum.kind == PowerExtremumKind.PEAK) 1 else -1
            drawPath(Path().apply {
                moveTo(x, y)
                lineTo(x - halfWidth, baseY)
                lineTo(x + halfWidth, baseY)
                close()
            }, color)
        }
    }
}
