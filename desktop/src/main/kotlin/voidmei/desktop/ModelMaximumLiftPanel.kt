package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import voidmei.fm.*
import java.util.Locale

@Composable
internal fun ModelMaximumLiftPanel(parameters: FlightModelParameters) {
    Text("350 km/h IAS 升力过载估算", style = MaterialTheme.typography.titleMedium)
    val model = parameters.stallSpeed
    if (model == null) {
        Text("缺少有效升力模型，无法估算")
        parameters.stallSpeedIssue?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        return
    }
    Text(String.format(Locale.ROOT, "模型基础质量：%.2f kg（不含燃油）", model.basicMassKg))
    val capacity = parameters.maximumFuelMassKg?.takeIf { it.isFinite() && it > 0 }
    val fuels = listOf("不含燃油" to 0.0) + if (capacity != null) listOf("半油" to capacity / 2, "满油" to capacity) else emptyList()
    if (capacity == null) Text("缺少有效燃油容量，仅显示不含燃油参考。", style = MaterialTheme.typography.bodySmall)
    var count by remember(model) { mutableStateOf(4) }
    Column {
        model.profiles.take(count).forEach { profile ->
            Text(String.format(Locale.ROOT, "后掠比例：%.4f", profile.sweep))
            fuels.forEach { (label, fuel) ->
                fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
                val clean = model.maximumLiftLoadAtIas(350.0, fuel, 0.0, profile.sweep)
                val full = model.maximumLiftLoadAtIas(350.0, fuel, 100.0, profile.sweep)
                Text(String.format(Locale.ROOT, "%s · 燃油 %.2f kg：", label, fuel) + "无襟翼 ${clean.shown()} G / 满襟翼 ${full.shown()} G")
            }
        }
    }
    if (count < model.profiles.size) TextButton(onClick = { count += 4 }) { Text("再显示 4 个后掠配置") }
    Text("按 (IAS / 对应质量与构型的 1 G 失速 IAS)² 估算；未计外挂、损伤及压缩性修正。不是结构过载限制；满襟翼参考工况可能超过襟翼限速。", style = MaterialTheme.typography.bodySmall)
    Text("此处没有单独计算 1000 米状态，不沿用旧“千米过载”的简化数值。", style = MaterialTheme.typography.bodySmall)
}
