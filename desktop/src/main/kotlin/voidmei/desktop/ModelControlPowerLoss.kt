package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale
import voidmei.fm.ControlPowerLoss

@Composable
internal fun ModelControlPowerLoss(loss: ControlPowerLoss) {
    fun Double?.display() = this?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "—"
    Text("三舵 PowerLoss 原始系数（旧称锁舵因数）", style = MaterialTheme.typography.titleMedium)
    Text("副翼 AileronPowerLoss：${loss.aileron.display()}")
    Text("升降舵 ElevatorPowerLoss：${loss.elevator.display()}")
    Text("方向舵 RudderPowerLoss：${loss.rudder.display()}")
    Text("保留模型原值，未换算为百分比；不代表当前锁舵状态，也不直接用于实时告警。缺失或无效时显示 —。", style = MaterialTheme.typography.bodySmall)
}
