package voidmei.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun PowerSpeedTypePanel(equivalent: Boolean, onChange: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!equivalent, { onChange(false) }, label = { Text("真空速 TAS") })
        FilterChip(equivalent, { onChange(true) }, label = { Text("等效空速 EAS") })
    }
    Text("EAS 按模型空气密度换算真空速后计算冲压；未作可压缩性修正，不等同于仪表 IAS。",
        style = MaterialTheme.typography.bodySmall)
}
