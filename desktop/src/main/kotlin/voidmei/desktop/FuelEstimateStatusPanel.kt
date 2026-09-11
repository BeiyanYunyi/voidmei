package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.ConnectionState
import voidmei.telemetry.HudField

@Composable
internal fun FuelEstimateStatusPanel(flight: ConnectionState.Flying, fields: List<HudField>) {
    val enduranceSelected = HudField.ENDURANCE in fields || HudField.ENDURANCE_CLOCK in fields
    if (!enduranceSelected && HudField.FUEL_LOSS_RATE !in fields) return
    val rate = flight.metrics.fuelConsumptionKgPerMinute?.takeIf { it.isFinite() && it >= 0 }
    val endurance = flight.metrics.fuelEnduranceSeconds?.takeIf { it.isFinite() && it >= 0 }
    val status = when {
        flight.telemetry.fuelKg?.let { it.isFinite() && it >= 0 } != true -> "缺少燃油量数据。"
        rate == null -> "等待连续燃油采样。"
        enduranceSelected && endurance == null && rate == 0.0 -> "采样期间未测得燃油减少，暂不能估算续航。"
        enduranceSelected && endurance == null -> "续航计算结果不可用。"
        else -> ""
    }
    Text(status + "最近 30 秒燃油变化，至少采样 10 秒；包含泄漏和抛弃油箱。",
        Modifier.testTag("hud-fuel-estimate-status"), style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
