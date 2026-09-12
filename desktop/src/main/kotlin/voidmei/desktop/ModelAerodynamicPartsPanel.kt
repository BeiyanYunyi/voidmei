package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.util.Locale
import voidmei.fm.*

@Composable
internal fun ModelAerodynamicPartsPanel(kind: AerodynamicPartKind, parts: List<AerodynamicPart>) {
    Text(kind.label, style = MaterialTheme.typography.titleMedium)
    val selected = parts.filter { it.kind == kind }
    if (selected.isEmpty()) { Text("未提供${kind.label}参数"); return }
    var count by remember(kind, parts) { mutableStateOf(8) }
    Text("显示 ${minOf(count, selected.size)} / ${selected.size} 个来源", style = MaterialTheme.typography.bodySmall)
    fun Double?.shown() = this?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "—"
    SelectionContainer { Column {
        selected.take(count).forEach { part ->
            Text("来源：${part.sourcePath}")
            if (part.sourcePath.contains("WingPlaneSweep", ignoreCase = true)) Text("后掠比例：${part.sweepRatio.shown()}")
            Text("CdMin：${part.cdMin.shown()} · Cl0：${part.cl0.shown()}")
            Text("原始临界迎角：${part.alphaLow.shown()} ～ ${part.alphaHigh.shown()} °")
            Text("临界升力系数：${part.clLow.shown()} ～ ${part.clHigh.shown()}")
        }
    } }
    if (count < selected.size) TextButton(onClick = { count += 8 }, modifier = Modifier.testTag("aero-parts-more-${kind.name}")) { Text("再显示 8 个来源") }
    Text("各来源及别名分别列出，不拼接缺失字段或插值；迎角为字段原值，未扣除安装角。Fin／Stab 沿用旧字段组归类，需按来源路径辨认器件。", style = MaterialTheme.typography.bodySmall)
}
