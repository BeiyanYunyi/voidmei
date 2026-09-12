package voidmei.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal typealias EngineFieldPreset = voidmei.telemetry.EngineFieldPreset

@Composable
internal fun HudEngineFieldPresets(tag: String, onChange: (List<String>) -> Unit) {
    Text("字段预设", style = MaterialTheme.typography.titleSmall)
    Text("替换当前字段及顺序，应用后仍可逐项编辑。动力量与耐热时需要匹配的 FM 数据；燃油、总重在飞行读数中设置。",
        style = MaterialTheme.typography.bodySmall)
    FlowRow {
        EngineFieldPreset.entries.forEach { preset ->
            TextButton({ onChange(preset.fields.toList()) }, Modifier.testTag("$tag-preset-${preset.name.lowercase()}")) {
                Text("应用${preset.label}")
            }
        }
    }
}
