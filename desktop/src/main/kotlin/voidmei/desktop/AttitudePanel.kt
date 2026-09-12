package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.AttitudeGeometry
import voidmei.telemetry.Telemetry
import voidmei.telemetry.AirflowMarker
import voidmei.telemetry.AirflowLimits
import voidmei.telemetry.AircraftAlertModel
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import java.util.Locale

@Composable
internal fun AttitudePanel(telemetry: Telemetry, compact: Boolean = false, model: AircraftAlertModel? = null, earthFixed: Boolean = false, showAoaLimits: Boolean = true, fillAvailable: Boolean = false, showNorthPointer: Boolean = false) {
    val attitude = AttitudeGeometry.fromIndicators(telemetry.pitchDeg, telemetry.rollDeg)
    val heading = AttitudeGeometry.heading(telemetry.headingDeg)
    val north = if (showNorthPointer) AttitudeGeometry.northDirection(telemetry.headingDeg) else null
    val marker = AirflowMarker.fromAngles(telemetry.angleOfAttackDeg, telemetry.sideslipAngleDeg)
    val airflowLimits = if (showAoaLimits) AirflowLimits.fromTelemetry(telemetry, model) else emptyList()
    val headingText = heading?.let { String.format(Locale.ROOT, "%03d", kotlin.math.round(it).toInt() % 360) } ?: "—"
    Column(if (fillAvailable) Modifier.fillMaxSize() else Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("姿态 · 航向 $headingText°")
        if (attitude == null) {
            Text("姿态数据不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val pitch = AttitudeGeometry.fromIndicators(telemetry.pitchDeg, 0.0)?.pitchDeg
            val roll = AttitudeGeometry.fromIndicators(0.0, telemetry.rollDeg)?.rollDeg
            fun angle(value: Double?) = readingNumber(value)
            Text("俯仰 ${angle(pitch)}° · 横滚 ${angle(roll)}°", style = MaterialTheme.typography.bodySmall)
        }
        else {
            val horizonAttitude = if (earthFixed) AttitudeGeometry(0.0, 0.0) else attitude
            val canvasModifier = if (fillAvailable) Modifier.fillMaxWidth().weight(1f)
                else Modifier.fillMaxWidth().height(if (compact) 110.dp else 190.dp)
            Canvas(canvasModifier.testTag("attitude-canvas")
                .semantics { stateDescription = if (earthFixed) "地面参考" else "机体参考"
                    contentDescription = (if (showNorthPointer) {
                        if (north == null) "指北针不可用，航向未知；" else "红色指北针，航向 $headingText°；"
                    } else "") + (when {
                    marker == null -> "姿态仪，迎角/侧滑十字不可用"
                    marker.outsideScale -> "姿态仪，迎角/侧滑十字超量程"
                    else -> "姿态仪，青色十字表示迎角/侧滑"
                }) + if (airflowLimits.isEmpty()) "" else "; 模型迎角限 ${airflowLimits.joinToString()}°" }) {
                val scale = size.height / 65
                val cx = size.width / 2
                val cy = size.height / 2
                val extent = size.width + size.height + 90 * scale
                clipRect {
                    rotate(-horizonAttitude.rollDeg.toFloat(), Offset(cx, cy)) {
                        val horizon = cy + horizonAttitude.pitchDeg.toFloat() * scale
                        drawRect(Color(0xFF1E526F), Offset(cx - extent, horizon - extent), Size(extent * 2, extent))
                        drawRect(Color(0xFF644E3C), Offset(cx - extent, horizon), Size(extent * 2, extent))
                    }
                    for (pitch in -90..90 step 10) {
                        val halfWidth = if (pitch == 0) size.width else if (pitch % 30 == 0) 42.dp.toPx() else 25.dp.toPx()
                        val left = horizonAttitude.project(-halfWidth.toDouble(), pitch.toDouble(), scale.toDouble())
                        val right = horizonAttitude.project(halfWidth.toDouble(), pitch.toDouble(), scale.toDouble())
                        drawLine(Color.White.copy(alpha = if (pitch == 0) 1f else 0.6f),
                            Offset(cx + left.first.toFloat(), cy + left.second.toFloat()),
                            Offset(cx + right.first.toFloat(), cy + right.second.toFloat()), if (pitch == 0) 2.dp.toPx() else 1.dp.toPx())
                    }
                    withTransform({
                        if (earthFixed) {
                            translate(top = -attitude.pitchDeg.toFloat() * scale)
                            rotate(attitude.rollDeg.toFloat(), Offset(cx, cy))
                        }
                    }) {
                        val aircraft = Color(0xFFFFD580)
                        val markerArm = 6.dp.toPx().coerceAtMost(size.minDimension / 4)
                        val verticalRange = (cy - markerArm - 2.dp.toPx()).coerceAtLeast(0f)
                        airflowLimits.forEach { angle ->
                            val y = cy + (angle / 30).toFloat() * verticalRange
                            drawLine(Color(0xFFFF6577), Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        }
                        drawLine(aircraft, Offset(cx - 50.dp.toPx(), cy), Offset(cx - 12.dp.toPx(), cy), 3.dp.toPx())
                        drawLine(aircraft, Offset(cx + 12.dp.toPx(), cy), Offset(cx + 50.dp.toPx(), cy), 3.dp.toPx())
                        drawCircle(aircraft, 3.dp.toPx(), Offset(cx, cy))
                        marker?.let {
                            val arm = markerArm
                            val x = cx + it.horizontal.toFloat() * (cx - arm - 2.dp.toPx()).coerceAtLeast(0f)
                            val y = cy + it.vertical.toFloat() * verticalRange
                            val color = if (it.outsideScale) Color(0xFFFF967B) else Color(0xFF84DEC6)
                            drawLine(color, Offset(x - arm, y), Offset(x + arm, y), 2.dp.toPx())
                            drawLine(color, Offset(x, y - arm), Offset(x, y + arm), 2.dp.toPx())
                        }
                    }
                    // Keep heading in screen coordinates; pitch/roll transforms only affect the horizon and aircraft.
                    north?.let { (x, y) ->
                        val center = Offset(cx, cy)
                        val vector = Offset(x.toFloat(), y.toFloat()) * (size.minDimension / 4)
                        drawLine(Color.White, center, center - vector, 2.dp.toPx())
                        drawLine(Color(0xFFFF6577), center, center + vector, 2.dp.toPx())
                    }
                }
            }
            if (showNorthPointer) Text(if (north == null) "指北针不可用：航向未知" else "红色指北，白色指南",
                style = MaterialTheme.typography.bodySmall)
            Text("俯仰 ${readingNumber(attitude.pitchDeg)}° · 横滚 ${readingNumber(attitude.rollDeg)}° · " +
                when { marker == null -> "迎角/侧滑未知"; marker.outsideScale -> "迎角/侧滑超量程"; else -> "青色十字：迎角/侧滑" } +
                if (airflowLimits.isEmpty()) "" else " · 红虚线：模型迎角限",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
