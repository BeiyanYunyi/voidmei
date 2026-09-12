package voidmei.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.ModelDetailSection

@Composable
internal fun ModelDetailVisibility(hidden: Set<ModelDetailSection>, onChange: (Set<ModelDetailSection>) -> Unit) {
    Text("模型显示分类", style = MaterialTheme.typography.titleMedium)
    Text("仅控制下方内容显示；模型计算、告警及比较数据不受影响。实时和离线模型页共用此设置。", style = MaterialTheme.typography.bodySmall)
    FlowRow {
        ModelDetailSection.entries.forEach { section ->
            FilterChip(section !in hidden, { onChange(if (section in hidden) hidden - section else hidden + section) },
                label = { Text(section.label) }, modifier = Modifier.testTag("model-section-${section.name}"))
        }
    }
    TextButton(enabled = hidden.isNotEmpty(), onClick = { onChange(emptySet()) }, modifier = Modifier.testTag("model-sections-all")) { Text("显示全部分类") }
    if (hidden.size == ModelDetailSection.entries.size) Text("所有分类已隐藏，可点击分类恢复；模型加载状态和参数提取错误仍显示。")
}
