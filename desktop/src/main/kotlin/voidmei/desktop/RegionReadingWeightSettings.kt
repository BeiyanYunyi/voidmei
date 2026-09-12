package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.HudRegion
import voidmei.config.ReadingTextWeights

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegionReadingWeightSettings(region: HudRegion, onChange: (ReadingTextWeights?) -> Unit) {
    val weights = region.readingTextWeights ?: ReadingTextWeights()
    Text("表格字重")
    listOf("标签" to weights.label, "数字" to weights.number, "单位" to weights.unit).forEachIndexed { index, (name, selected) ->
        Text(name)
        FlowRow {
            listOf(null to if (index == 2) "跟随数字" else "默认", 400 to "常规", 700 to "粗体").forEach { (weight, label) ->
                FilterChip(selected == weight, onClick = {
                    val next = when (index) {
                        0 -> weights.copy(label = weight)
                        1 -> weights.copy(number = weight)
                        else -> weights.copy(unit = weight)
                    }
                    onChange(next.takeUnless { it == ReadingTextWeights() })
                }, label = { Text(label) }, modifier = Modifier.testTag("reading-weight-${region.id}-$index-${weight ?: "inherit"}"))
            }
        }
    }
    TextButton(enabled = region.readingTextWeights != null, onClick = { onChange(null) },
        modifier = Modifier.testTag("reading-weights-${region.id}-reset")) { Text("恢复默认表格字重") }
}
