package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import voidmei.telemetry.FlightAlert
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import voidmei.telemetry.AircraftAlertModel
import voidmei.telemetry.Telemetry
import java.util.Locale

@Composable
internal fun MechanizationPanel(telemetry: Telemetry, model: AircraftAlertModel?,
    showGear: Boolean = true, showFlaps: Boolean = true, showAirbrake: Boolean = true, automaticSweep: Boolean = false, alerts: List<FlightAlert> = emptyList(), showFlapBar: Boolean = false) {
    if (!showGear && !showFlaps && !showAirbrake && !showFlapBar) return
    fun Double?.validPercent() = this?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val gearPercent = telemetry.gearPercent.validPercent()
    val flapsPercent = telemetry.flapsPercent.validPercent()
    val airbrakePercent = telemetry.airbrakePercent.validPercent()
    val parameters = model?.parametersFor(telemetry.aircraft)
    val flaps = parameters?.flapLimits
    val showSweep = automaticSweep && parameters?.variableSweep == true && flapsPercent == 0.0
    val sweepPercent = telemetry.wingSweepRatio?.takeIf { it.isFinite() && it in 0.0..1.0 }?.times(100)
    fun Double?.shown(): String = this?.takeIf { it.isFinite() }?.let {
        String.format(Locale.ROOT, "%.1f", it)
    } ?: "—"
    fun Double?.deployed() = this != null && isFinite() && this > 0 && this <= 100
    val gearWarning = showGear && gearPercent.deployed() && FlightAlert.GEAR_LIMIT in alerts
    val flapWarning = showFlaps && !showSweep && flapsPercent.deployed() && FlightAlert.FLAP_LIMIT in alerts
    val airbrakeWarning = showAirbrake && airbrakePercent != null && airbrakePercent >= 90 &&
        gearPercent != null && gearPercent < 100 && FlightAlert.AIRBRAKE_EXTENDED in alerts
    val warningText = listOfNotNull(
        FlightAlert.GEAR_LIMIT.label.takeIf { gearWarning },
        FlightAlert.FLAP_LIMIT.label.takeIf { flapWarning },
        FlightAlert.AIRBRAKE_EXTENDED.label.takeIf { airbrakeWarning }).joinToString("；")
    val normal = Color(0xFF84DEC6)
    val warning = MaterialTheme.colorScheme.error
    val parts = listOfNotNull(
        if (showGear) "起落架 ${gearPercent.shown()}%" to gearWarning else null,
        if (showFlaps) (if (showSweep) "后掠 ${sweepPercent.shown()}%" else "襟翼 ${flapsPercent.shown()}%") to flapWarning else null,
        if (showAirbrake) "减速板 ${airbrakePercent.shown()}%" to airbrakeWarning else null)
    Column {
        if (parts.isNotEmpty()) Text(buildAnnotatedString {
            parts.forEachIndexed { index, (text, warned) ->
                if (index > 0) append("   ")
                withStyle(SpanStyle(color = if (warned) warning else normal)) { append(text) }
            }
        }, modifier = Modifier.semantics { if (warningText.isNotEmpty()) stateDescription = warningText },
            color = normal, fontSize = 13.sp)
        if (showFlapBar && flapsPercent == null) {
            Text("襟翼开度条 · 数据不可用", color = Color(0xFF9EB1C0), fontSize = 13.sp)
        }
        if (showFlapBar && flapsPercent != null) {
            val maximum = flaps?.maximumPercentAt(telemetry.iasKmh)
            if (!showFlaps || showSweep) {
                Text("襟翼开度条 · ${flapsPercent.shown()}%", color = normal, fontSize = 13.sp)
                Text("表内最大开度 ${maximum.shown()}%", color = Color(0xFF9EB1C0), fontSize = 13.sp)
            }
            FlapPositionBar(flapsPercent, maximum)
        }
        if (showFlaps && !showSweep) Text("当前 IAS 下表内最大襟翼 ${flaps?.maximumPercentAt(telemetry.iasKmh).shown()}%",
            color = Color(0xFF9EB1C0), fontSize = 13.sp)
        if (showFlaps && !showSweep) Text("当前襟翼开度的模型限速 ${flaps?.speedAt(flapsPercent).shown()} km/h",
            color = Color(0xFF9EB1C0), fontSize = 13.sp)
    }
}
