package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.*

@Composable
internal fun MassEstimateStatusPanel(flight: ConnectionState.Flying, fields: List<HudField>, model: AircraftAlertModel?) {
    if (HudField.MASS_ESTIMATE !in fields && HudField.FUEL_MASS_SHARE !in fields) return
    val mass = model?.parametersFor(flight.telemetry.aircraft)?.basicMassKg
    val status = when {
        mass?.let { it.isFinite() && it > 0 } != true -> "缺少当前机型的有效基础质量模型。"
        flight.telemetry.fuelKg?.let { it.isFinite() && it >= 0 } != true -> "缺少有效燃油量数据。"
        HudField.MASS_ESTIMATE.value(flight, model) == null -> "质量计算结果不可用。"
        else -> ""
    }
    Text(status + "质量按模型基础质量加燃油估计，不计弹药、外挂和损伤。" +
        if (HudField.FUEL_MASS_SHARE in fields) "燃油占比条满刻度 50%；超过刻度仍显示实际占比读数。" else "",
        Modifier.testTag("hud-mass-estimate-status"), style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
