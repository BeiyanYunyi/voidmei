package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale
import voidmei.fm.FlapLimits

@Composable
internal fun ModelFlapLimitsTable(limits: FlapLimits?) {
    Text("模型襟翼限速数据点", style = MaterialTheme.typography.titleMedium)
    if (limits == null) {
        Text("模型未提供有效襟翼限速数据点")
        return
    }
    var count by remember(limits) { mutableStateOf(8) }
    val visible = limits.points.take(count)
    Text("显示 ${visible.size} / ${limits.points.size} 个数据点", style = MaterialTheme.typography.bodySmall)
    SelectionContainer {
        Column(Modifier.fillMaxWidth().testTag("model-flap-limit-table"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("襟翼开度 (%)", Modifier.weight(1f))
                Text("模型限速 IAS (km/h)", Modifier.weight(1f))
            }
            visible.forEachIndexed { index, point ->
                Row(Modifier.fillMaxWidth().testTag("model-flap-limit-row-$index")) {
                    Text(String.format(Locale.ROOT, "%.2f", point.ratio * 100), Modifier.weight(1f))
                    Text(String.format(Locale.ROOT, "%.2f", point.speedKmh), Modifier.weight(1f))
                }
            }
        }
    }
    if (visible.size < limits.points.size) TextButton(onClick = { count += 8 }, modifier = Modifier.testTag("model-flap-limits-more")) {
        Text("再显示 8 个数据点")
    }
    Text("仅列出有效模型数据点，开度从小到大排列，显示保留两位小数；不补造档位。当前开度的估算在表内插值，表外不外推。", style = MaterialTheme.typography.bodySmall)
}
