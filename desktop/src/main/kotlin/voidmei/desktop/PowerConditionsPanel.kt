package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun PowerConditionsPanel(speed: Double, temperature: Double, equivalent: Boolean,
    onApply: (Double, Double) -> Unit) {
    var speedDraft by rememberSaveable(speed) { mutableStateOf(speed.toString()) }
    var temperatureDraft by rememberSaveable(temperature) { mutableStateOf(temperature.toString()) }
    var error by rememberSaveable(speedDraft, temperatureDraft) { mutableStateOf(false) }
    val speedType = if (equivalent) "EAS" else "TAS"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(speedDraft, { speedDraft = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("自定义速度 $speedType (km/h)") })
        OutlinedTextField(temperatureDraft, { temperatureDraft = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("自定义海平面温度 (°C)") })
        TextButton(onClick = {
            val nextSpeed = speedDraft.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            val nextTemperature = temperatureDraft.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > -273.15 }
            if (nextSpeed == null || nextTemperature == null) error = true
            else { error = false; onApply(nextSpeed, nextTemperature) }
        }) { Text("应用功率条件") }
        if (error) Text("速度需为非负有限数，海平面温度需为大于 -273.15°C 的有限数。", color = MaterialTheme.colorScheme.error)
        Text(String.format(Locale.ROOT, "已应用：%s %.2f km/h · 海平面 %.2f°C", speedType, speed, temperature),
            style = MaterialTheme.typography.bodySmall)
    }
}
