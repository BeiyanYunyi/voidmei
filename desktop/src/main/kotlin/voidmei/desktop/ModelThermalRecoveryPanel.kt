package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale
import voidmei.fm.*

@Composable
internal fun ModelThermalRecoveryPanel(models: List<EngineThermalParameters>) {
    Text("耐热恢复模型", style = MaterialTheme.typography.titleMedium)
    if (models.isEmpty()) { Text("没有可用发动机耐热恢复数据"); return }
    Text("按 WorkTime / RecoverTime 计算，单位为工作预算秒／恢复秒（s/s），不是当前恢复速度或剩余寿命。仅已知且恢复时长大于零的档位参与平均，零工作时长按零计入。", style = MaterialTheme.typography.bodySmall)
    models.forEach { model ->
        key(model.telemetryIndex) {
            val summary = model.recoverySummary()
            val mean = summary.meanRate?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"
            Text("发动机 #${model.telemetryIndex} 有效档位算术平均：$mean s/s")
            Text("参与 ${summary.validBands} / ${summary.totalBands} 个档位", style = MaterialTheme.typography.bodySmall)
            var expanded by remember(model) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起 #${model.telemetryIndex} 恢复档位" else "查看 #${model.telemetryIndex} 恢复档位") }
            if (expanded) model.bands.forEach { band ->
                val rate = band.recoveryRate()?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"
                Text("Load${band.index}：$rate s/s")
            }
        }
    }
    Text("旧平均值的固定分母与此处不同；缺失或零恢复时长不补零参与平均。", style = MaterialTheme.typography.bodySmall)
}
