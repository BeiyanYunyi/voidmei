package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale
import voidmei.fm.*

@Composable
internal fun ModelDragPanel(parameters: FlightModelParameters) {
    Text("阻力参数", style = MaterialTheme.typography.titleMedium)
    fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "—"
    val references = remember(parameters) { parameters.dragReferences() }
    var count by remember(parameters) { mutableStateOf(4) }
    if (references.isEmpty()) Text("缺少机翼几何来源，阻力参考未知")
    references.take(count).forEach { reference ->
        Text("阻力几何来源：${reference.geometrySource}")
        Text("无襟翼极曲线：${reference.cleanSource ?: "未知或不唯一"} · 机身极曲线：${reference.bodySource ?: "未知或不唯一"}")
        Text("主阻力面积 CdS：${reference.dragArea.shown()} m² · 诱导阻力因数 k：${reference.inducedFactor.shown()}")
        Text("半油参考质量：${reference.halfFuelMassKg.shown()} kg")
        Text("CdS／质量：${reference.areaPerTonne.shown()} m²/t · 质量 × k：${reference.massTimesInducedFactor.shown()} kg")
    }
    if (count < references.size) TextButton(onClick = { count += 4 }) { Text("再显示 4 个阻力来源") }
    Text("散热器与油冷器原始阻力系数", style = MaterialTheme.typography.titleSmall)
    if (parameters.radiatorDrag.isEmpty()) Text("未提供散热器阻力系数")
    parameters.radiatorDrag.forEach { Text("${it.sourcePath}：${it.coefficient.shown()}") }
    Text("CdS = 机翼面积 × 无襟翼 CdMin + 机身面积 × 机身 CdMin；k = 1／(π × 展弦比 × Oswald 效率因数)。半油质量含空重、机油、满加力燃料和一半燃油容量。缺失或不唯一的来源保持未知，不跨后掠配置取值。质量归一化值沿用旧参考公式，不是实际加速度；未计散热器开度、起落架、挂载或跨音速阻力。", style = MaterialTheme.typography.bodySmall)
}
