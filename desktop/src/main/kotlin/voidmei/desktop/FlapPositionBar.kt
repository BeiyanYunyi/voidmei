package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun FlapPositionBar(percent: Double?, maximum: Double?) {
    val position = percent?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return
    val limit = maximum?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val background = MaterialTheme.colorScheme.surfaceVariant
    val limitColor = MaterialTheme.colorScheme.error
    val fill = if (limit != null && position > limit) limitColor else MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(20.dp).testTag("flap-position-bar").semantics {
        contentDescription = "襟翼开度；" + (limit?.let { "表内最大开度 ${String.format(Locale.ROOT, "%.1f", it)}%" } ?: "模型最大开度未知")
        progressBarRangeInfo = ProgressBarRangeInfo((position / 100).toFloat(), 0f..1f)
    }) {
        val top = 4.dp.toPx()
        val height = 12.dp.toPx()
        drawRect(background, Offset(0f, top), Size(size.width, height))
        drawRect(fill, Offset(0f, top), Size(size.width * (position / 100).toFloat(), height))
        limit?.let {
            val inset = minOf(1.dp.toPx(), size.width / 2)
            val x = (size.width * (it / 100).toFloat()).coerceIn(inset, size.width - inset)
            drawLine(limitColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
}
