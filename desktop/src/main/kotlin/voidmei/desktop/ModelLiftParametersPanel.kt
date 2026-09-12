package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale
import voidmei.fm.*

@Composable
internal fun ModelLiftParametersPanel(parameters: FlightModelParameters) {
    Text("升力与几何参数", style = MaterialTheme.typography.titleMedium)
    fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "—"
    var count by remember(parameters.aerodynamicGeometry) { mutableStateOf(4) }
    if (parameters.aerodynamicGeometry.isEmpty()) Text("未提供可识别的机翼几何来源")
    parameters.aerodynamicGeometry.take(count).forEach { wing ->
        Text("机翼来源：${wing.sourcePath}")
        Text("后掠比例：${wing.sweepRatio.shown()} · 后掠角：${wing.sweptAngle.shown()} °")
        Text("机翼面积：${wing.wingArea.shown()} m² · 翼展：${wing.span.shown()} m · 展弦比：${wing.aspectRatio.shown()}")
        Text("机身面积：${wing.bodyArea.shown()} m² · 来源：${wing.bodySource ?: "未知"}")
        Text("Oswald 效率因数：${wing.efficiency.shown()} · 来源：${wing.efficiencySource ?: "未知"}")
    }
    if (count < parameters.aerodynamicGeometry.size) TextButton(onClick = { count += 4 }) { Text("再显示 4 个几何来源") }
    Text("失速模型采用的有效升力面积", style = MaterialTheme.typography.titleSmall)
    val model = parameters.stallSpeed
    if (model == null) Text("缺少完整升力模型，派生升力面积未知")
    else model.profiles.forEach { profile ->
        Text("后掠 ${profile.sweep.shown()}：无襟翼 ${profile.cleanArea.shown()} / 满襟翼 ${profile.fullFlapArea.shown()} m²")
        Text("按空重归一化：${profile.areaPerTonne(parameters.emptyMassKg, false).shown()} / ${profile.areaPerTonne(parameters.emptyMassKg, true).shown()} m²/t")
    }
    Text("面积总和要求全部九项机翼分量，缺失不补零；展弦比为翼展²／面积。几何来源分别展示，派生升力面积复用已验证失速模型，包含系数贡献，不等于机翼几何面积。", style = MaterialTheme.typography.bodySmall)
}
