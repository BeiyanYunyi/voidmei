package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import voidmei.telemetry.*

@Composable
internal fun WepFuelStatusPanel(flight: ConnectionState.Flying, fields: List<HudField>, model: AircraftAlertModel?) {
    if (fields.none { it == HudField.WEP_FUEL || it == HudField.WEP_TIME }) return
    val parameters = model?.parametersFor(flight.telemetry.aircraft)?.wepFuel
    val estimate = flight.metrics.wepFuel?.estimateFor(flight, model)
    val status = when {
        parameters == null -> "当前机型缺少可用 WEP 燃料模型。"
        estimate == null -> "暂无有效 WEP 估算，请检查模型与发动机、油门数据。"
        HudField.WEP_TIME in fields && estimate.maximumSecondsAtCurrentRate == null -> {
            val consuming = flight.telemetry.engines.any { engine ->
                engine.throttlePercent?.let { it > 100 } == true &&
                    (parameters.consumptionKgPerSecond[engine.index] ?: 0.0) > 0
            }
            if (consuming) "当前 WEP 续航上限无法计算。" else "当前未观测到 WEP 消耗，续航时间未知。"
        }
        else -> null
    }
    val color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0)
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
    Text("WEP 为估算上限：连接前已用量未知；按模型容量扣除观察到的消耗。断流后重新估算，不表示实际补满。",
        style = MaterialTheme.typography.bodySmall, color = color)
}
