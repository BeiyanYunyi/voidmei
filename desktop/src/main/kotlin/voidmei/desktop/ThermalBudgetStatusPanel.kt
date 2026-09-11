package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.*

@Composable
internal fun ThermalBudgetStatusPanel(flight: ConnectionState.Flying, model: AircraftAlertModel?, thermal: EngineThermalObservation?,
    engineIndex: Int = 1) {
    val reason = when {
        model?.parametersFor(flight.telemetry.aircraft)?.engineThermals?.singleOrNull { it.telemetryIndex == engineIndex } == null ->
            "缺少 $engineIndex 号发动机温度模型。"
        thermal?.hudBudget(flight, model, engineIndex) == null -> "当前无可用的 $engineIndex 号发动机计时预算。"
        else -> ""
    }
    Text(reason + "热预算区间包含未知初始损耗，按采样温度估算，不是实际剩余寿命。",
        Modifier.testTag("hud-thermal-budget-status"), style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
