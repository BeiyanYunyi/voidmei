package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import voidmei.fm.EngineThermalParameters
import java.util.Locale
import voidmei.telemetry.*

internal fun formatThermalBudget(range: ThermalBudgetRange?): String {
    val rounded = range?.roundForDisplay() ?: return "— s"
    return String.format(Locale.ROOT, "%.1f–%.1f s", rounded.minimumSeconds, rounded.maximumSeconds)
}

@Composable
internal fun EngineThermalBudgetPanel(budgets: List<EngineThermalBudget>) {
    if (budgets.isEmpty()) return
    Text("温度工作预算估算", style = MaterialTheme.typography.titleSmall)
    Text("范围包含未知初始损耗；按采样温度估算，不是实际剩余寿命。断流后重新估算。",
        style = MaterialTheme.typography.bodySmall)
    budgets.forEach { engine ->
        fun shown(channel: ThermalChannelBudget?): String {
            if (channel == null) return "未知"
            val range = channel.activeRemaining ?: return "当前无计时档位"
            return formatThermalBudget(range)
        }
        Text("#${engine.telemetryIndex} · WaterTemperature ${shown(engine.water)} · OilTemperature ${shown(engine.oil)}")
    }
}

@Composable
internal fun EngineThermalPanel(models: List<EngineThermalParameters>) {
    if (models.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("发动机温度模型") }
    if (!expanded) return
    Text("以下为模型阈值与工作/恢复时长，不是剩余寿命。WaterTemperature 是 FM 通道名称，喷气发动机不应解释为水温。",
        style = MaterialTheme.typography.bodySmall)
    fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "未知"
    models.forEach { model ->
        Text("发动机 #${model.telemetryIndex} 温度档位", style = MaterialTheme.typography.titleSmall)
        model.bands.forEach { band ->
            Text("Load${band.index} · WaterTemperature ${band.waterTemperatureC.shown()} °C · OilTemperature ${band.oilTemperatureC.shown()} °C")
            Text("模型工作时长 ${band.workSeconds.shown()} s · 恢复时长 ${band.recoverSeconds.shown()} s",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
