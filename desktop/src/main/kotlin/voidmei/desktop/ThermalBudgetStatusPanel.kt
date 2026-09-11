package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.*

@Composable
internal fun ThermalBudgetStatusPanel(flight: ConnectionState.Flying, model: AircraftAlertModel?, thermal: EngineThermalObservation?,
    engineIndex: Int = 1) {
    val parameters = model?.parametersFor(flight.telemetry.aircraft)?.engineThermals?.singleOrNull { it.telemetryIndex == engineIndex }
    val reason = when {
        parameters == null ->
            "缺少 $engineIndex 号发动机温度模型。"
        thermal?.hudBudget(flight, model, engineIndex) == null -> {
            val engine = flight.telemetry.engines.singleOrNull { it.index == engineIndex }
            val water = parameters.bands.any { it.waterTemperatureC != null && (it.workSeconds ?: 0.0) > 0 }
            val oil = parameters.bands.any { it.oilTemperatureC != null && (it.workSeconds ?: 0.0) > 0 }
            val missing = buildList {
                if (water && engine?.waterTemperatureC?.let { it.isFinite() && it >= -273.15 } != true) add("水温")
                if (oil && engine?.oilTemperatureC?.let { it.isFinite() && it >= -273.15 } != true) add("油温")
            }
            val budget = thermal?.budgetsFor(flight, model)?.singleOrNull { it.telemetryIndex == engineIndex }
            val channels = listOfNotNull(budget?.water, budget?.oil)
            val detail = when {
                engine == null -> "未获得该编号的唯一发动机数据。"
                !water && !oil -> "模型未提供有效计时档位。"
                missing.isNotEmpty() -> "缺少有效${missing.joinToString("、")}（°C）。"
                channels.isNotEmpty() && channels.all { channel -> channel.bands.isNotEmpty() && channel.bands.none { it.active } } ->
                    "当前温度未进入模型计时档位。"
                else -> "尚无与当前数据匹配的有效预算。"
            }
            "当前无可用的 $engineIndex 号发动机计时预算。$detail"
        }
        else -> ""
    }
    HudOverlayText(reason + "热预算区间包含未知初始损耗，按采样温度估算，不是实际剩余寿命。",
        Modifier.testTag("hud-thermal-budget-status"), style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
