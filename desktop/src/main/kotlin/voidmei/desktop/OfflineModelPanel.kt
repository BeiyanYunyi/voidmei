package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun OfflineModelPanel(initialDataRoot: String) {
    var root by remember { mutableStateOf(initialDataRoot) }
    var rootDraft by remember { mutableStateOf(initialDataRoot) }
    var draft by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentModel by remember(root, selected) { mutableStateOf<NamedModel?>(null) }
    var baseline by remember(root) { mutableStateOf<NamedModel?>(null) }
    Text("离线模型查看", style = MaterialTheme.typography.headlineSmall)
    Text("输入解包文件中的机型编号（不含 .blkx），查看参数、字段与功率/推力曲线。此窗口没有实时飞行数据；目录和机型选择仅用于本窗口。",
        style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(rootDraft, { rootDraft = it }, Modifier.weight(1f), label = { Text("离线数据目录") }, singleLine = true)
        Button(enabled = rootDraft.isNotBlank(), onClick = { root = rootDraft }) { Text("应用离线目录") }
    }
    key(root) { AircraftCatalogPanel(root) { draft = it; selected = it; error = null } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft, { draft = it; error = null }, Modifier.weight(1f),
            label = { Text("离线机型编号") }, singleLine = true)
        Button(enabled = draft.isNotBlank(), onClick = {
            val name = draft.trim().lowercase(Locale.ROOT)
            if (name.length > 128 || !Regex("[a-z0-9_-]+").matches(name)) {
                error = "机型编号仅支持字母、数字、下划线和连字符，最多 128 个字符。"
            } else { selected = name; error = null }
        }) { Text("查看机型") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    TextButton(enabled = currentModel != null, onClick = { baseline = currentModel }) { Text("设为比较基准") }
    baseline?.let { saved ->
        Text("比较基准：${saved.aircraft}")
        TextButton(onClick = { baseline = null }) { Text("清除比较基准") }
        ModelComparisonPanel(saved, currentModel)
    }
    selected?.let { aircraft ->
        Text("查看机型：$aircraft")
        FlightModelPanel(null, root, onModel = { _, _ -> }, onDataRoot = { root = it }, aircraftOverride = aircraft,
            showDirectoryControls = false, onSnapshot = { currentModel = it?.takeIf { model -> model.aircraft == aircraft } })
    } ?: Text("数据目录：$root")
}
