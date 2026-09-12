package voidmei.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Draw inside the existing 12 dp padding so toggling a border never reflows content. */
internal fun Modifier.hudRegionBorder(enabled: Boolean, alpha: Float): Modifier = if (!enabled || alpha == 0f) this else drawWithCache {
    val step = 1.dp.toPx()
    onDrawWithContent {
        drawContent()
        for (index in 0..5) {
            val inset = (index + .5f) * step
            drawRoundRect(Color.Black.copy(alpha = alpha * (index + 1) / 12f),
                Offset(inset, inset), Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                CornerRadius((10 - index) * step), style = Stroke(step))
        }
        val inset = 6.5f * step
        drawRoundRect(Color(0xFFB8CCD9).copy(alpha = alpha), Offset(inset, inset),
            Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
            CornerRadius(4 * step), style = Stroke(step))
    }
}
