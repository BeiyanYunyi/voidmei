package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voidmei.telemetry.AttitudeGeometry
import kotlin.math.*

@Composable
internal fun CompassPanel(degrees: Double?, headingUp: Boolean = false, plotModifier: Modifier? = null) {
    val heading = AttitudeGeometry.heading(degrees) ?: return
    val measurer = rememberTextMeasurer()
    val labels = listOf("北", "东", "南", "西").map {
        measurer.measure(it, TextStyle(color = Color.White, fontSize = 12.sp))
    }
    val labelExtent = labels.maxOf { maxOf(it.size.width, it.size.height) }.toFloat()
    val density = LocalDensity.current
    val height = with(density) { maxOf(140.dp, (labelExtent * 2 + 92.dp.toPx()).toDp()) }
    Canvas((plotModifier ?: Modifier.fillMaxWidth().height(height)).testTag("hud-compass").semantics {
        contentDescription = "${if (headingUp) "航向朝上罗盘" else "固定北向罗盘"}，航向 ${round(heading).toInt() % 360}°"
    }) {
        val center = Offset(size.width / 2, size.height / 2)
        val geometry = compassLabelGeometry(size, labelExtent, 4.dp.toPx())
        val radius = geometry.first
        val rotation = if (headingUp) -heading * PI / 180 else 0.0
        drawCircle(Color(0xFF9EB1C0), radius, center, style = Stroke(1.dp.toPx()))
        for (step in 0 until 12) {
            val angle = step * PI / 6 + rotation
            val unit = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
            drawLine(Color(0xFF9EB1C0), center + unit * (radius - 5.dp.toPx()).coerceAtLeast(0f), center + unit * radius, 1.dp.toPx())
        }
        labels.forEachIndexed { index, layout ->
            val angle = index * PI / 2 + rotation
            val point = center + Offset(sin(angle).toFloat(), -cos(angle).toFloat()) * geometry.second
            drawText(layout, topLeft = point - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        val angle = if (headingUp) 0.0 else heading * PI / 180
        val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
        val side = Offset(-direction.y, direction.x)
        val tip = center + direction * radius
        val tail = center - direction * (radius * 0.35f)
        val color = Color(0xFFFFD580)
        drawLine(color, tail, tip, 2.dp.toPx())
        val arm = minOf(8.dp.toPx(), radius / 3)
        drawLine(color, tip, tip - direction * arm + side * (arm / 2), 2.dp.toPx())
        drawLine(color, tip, tip - direction * arm - side * (arm / 2), 2.dp.toPx())
    }
}

/** Circle radius and cardinal-label centre distance, accounting for measured text extent. */
internal fun compassLabelGeometry(size: Size, labelExtent: Float, gap: Float): Pair<Float, Float> {
    val half = size.minDimension.coerceAtLeast(0f) / 2
    val extent = labelExtent.coerceAtLeast(0f)
    val radius = (half - extent - gap.coerceAtLeast(0f)).coerceAtLeast(0f)
    val distance = (radius + gap.coerceAtLeast(0f) + extent / 2).coerceAtMost((half - extent / 2).coerceAtLeast(0f))
    return radius to distance
}
