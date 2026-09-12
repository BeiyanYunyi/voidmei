package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale
import voidmei.fm.WepFuelModel

@Composable
internal fun ModelWepFuelPanel(model: WepFuelModel?) {
    Text("加力燃料模型", style = MaterialTheme.typography.titleMedium)
    val duration = model?.fullConsumptionDurationSeconds()
    if (model == null || duration == null) {
        Text("未提供可计算的加力燃料模型")
        return
    }
    Text(String.format(Locale.ROOT, "整机共享加力燃料容量：%.2f kg", model.capacityKg))
    model.consumptionKgPerSecond.toSortedMap().forEach { (index, rate) ->
        Text(String.format(Locale.ROOT, "发动机 #%d 模型消耗率：%.4f kg/s", index, rate))
    }
    Text(String.format(Locale.ROOT, "全发动机持续加力理论时限：%.2f 分钟", duration / 60))
    Text("按满共享燃料和所有发动机同时持续消耗估算；零消耗发动机不增加总消耗。这是静态模型信息，不是当前剩余燃料或加力时间。", style = MaterialTheme.typography.bodySmall)
}
