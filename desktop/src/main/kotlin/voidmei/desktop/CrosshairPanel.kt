package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun CrosshairPanel(sizeDp: Int, modifier: Modifier = Modifier, right: Boolean = false) {
    Canvas(modifier.testTag("hud-crosshair").semantics { contentDescription = if (right) "HUD 右侧线框准星" else "HUD 中心线框准星" }) {
        val desired = sizeDp.dp.toPx() / 2
        val stroke = (desired / 30).coerceAtLeast(1.dp.toPx())
        val outer = stroke + 2.dp.toPx()
        val extent = minOf(desired, (size.minDimension - outer) / 2).coerceAtLeast(0f)
        if (extent == 0f) return@Canvas
        val target = if (right) Offset(size.width - extent - outer / 2, center.y) else center
        for ((color, width) in listOf(Color.Black.copy(alpha = .6f) to stroke + 2.dp.toPx(), Color(0xFFFFD708) to stroke)) {
            drawCircle(color, extent / 2, target, style = Stroke(width))
            for (direction in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)))
                drawLine(color, target + direction * (extent / 4), target + direction * extent, width, StrokeCap.Round)
        }
    }
}
