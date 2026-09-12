package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import voidmei.config.OfflineModelPreferences
import androidx.compose.ui.platform.testTag

@Composable
internal fun OfflineModelPanel(initialDataRoot: String, hiddenSections: Set<voidmei.config.ModelDetailSection> = emptySet(),
    preferences: OfflineModelPreferences = OfflineModelPreferences(),
    onPreferences: (OfflineModelPreferences) -> Unit = {},
    onHiddenSections: ((Set<voidmei.config.ModelDetailSection>) -> Unit)? = null) {
    var saved by remember(preferences) { mutableStateOf(preferences) }
    fun save(next: OfflineModelPreferences) {
        val applied = next.copy(dataRoot = next.dataRoot ?: initialDataRoot)
        saved = applied; onPreferences(applied)
    }
    val root = saved.dataRoot ?: initialDataRoot
    val selected = saved.aircraft
    var rootDraft by remember(root) { mutableStateOf(root) }
    var draft by remember(selected) { mutableStateOf(selected.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentModel by remember(root, selected) { mutableStateOf<NamedModel?>(null) }
    var captured by remember(root) { mutableStateOf<CapturedOfflineBaseline?>(null) }
    val baseline = captured?.takeIf { it.root == root && it.model.aircraft == saved.baselineAircraft }?.model
    Text("离线模型查看", style = MaterialTheme.typography.headlineSmall)
    Text("输入解包文件中的机型编号（不含 .blkx），查看参数、字段与功率/推力曲线。此窗口没有实时飞行数据；已应用的目录、机型和比较基准编号会保存，供下次打开此窗口恢复；不会改变实时模型目录。",
        style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(rootDraft, { rootDraft = it }, Modifier.weight(1f), label = { Text("离线数据目录") }, singleLine = true)
        Button(enabled = rootDraft.isNotBlank() && rootDraft.length <= 4096 && rootDraft.none { it.isISOControl() }, onClick = { save(saved.copy(dataRoot = rootDraft)) }) { Text("应用离线目录") }
    }
    key(root) { AircraftCatalogPanel(root) { save(saved.copy(aircraft = it)); error = null } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft, { draft = it; error = null }, Modifier.weight(1f),
            label = { Text("离线机型编号") }, singleLine = true)
        Button(enabled = draft.isNotBlank(), onClick = {
            val name = draft.trim().lowercase(Locale.ROOT)
            if (name.length > 128 || !Regex("[a-z0-9_-]+").matches(name)) {
                error = "机型编号仅支持字母、数字、下划线和连字符，最多 128 个字符。"
            } else { save(saved.copy(aircraft = name)); error = null }
        }) { Text("查看机型") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    TextButton(enabled = currentModel != null, onClick = {
        currentModel?.let { model ->
            captured = CapturedOfflineBaseline(root, model)
            save(saved.copy(baselineAircraft = model.aircraft))
        }
    }) { Text("设为比较基准") }
    if (saved.baselineAircraft != null) {
        Text("下次打开会从文件重新加载基准；当前快照、燃油修正和曲线输入不随编号保存。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { captured = null; save(saved.copy(baselineAircraft = null)) }) { Text("清除比较基准") }
        if (baseline == null) RestoredOfflineBaseline(root, saved.baselineAircraft!!) { model ->
            captured = CapturedOfflineBaseline(root, model)
        }
    }
    TextButton(enabled = selected != null, onClick = { save(saved.copy(aircraft = null)); error = null }) { Text("清除当前离线机型") }
    baseline?.let { saved ->
        Text("比较基准：${saved.aircraft}")
        ModelComparisonPanel(saved, currentModel)
    }
    selected?.let { aircraft ->
        Text("查看机型：$aircraft")
        FlightModelPanel(null, root, onModel = { _, _ -> }, onDataRoot = { save(saved.copy(dataRoot = it)) }, aircraftOverride = aircraft,
            hiddenSections = hiddenSections, onHiddenSections = onHiddenSections, showDirectoryControls = false, onSnapshot = { currentModel = it?.takeIf { model -> model.aircraft == aircraft } })
    } ?: Text("数据目录：$root")
}

private data class CapturedOfflineBaseline(val root: String, val model: NamedModel)

@Composable
private fun RestoredOfflineBaseline(root: String, aircraft: String, onReady: (NamedModel) -> Unit) {
    val session = rememberFlightModelSession(aircraft, root)
    val ready = session.state as? FlightModelState.Ready
    val parameters = session.parameterResult?.getOrNull()?.parameters
    val details = session.detailResult?.getOrNull()
    LaunchedEffect(ready, parameters, details) {
        if (ready != null && parameters != null && details != null) onReady(NamedModel(ready.aircraft, parameters,
            details.jets.engines, ready.dataDirectory?.let { java.nio.file.Path.of(it).resolve(ready.source).toString() } ?: ready.source,
            java.time.Instant.now()))
    }
    val message = when (val state = session.state) {
        is FlightModelState.Missing -> "未找到比较基准 $aircraft：${state.path}"
        is FlightModelState.Invalid -> "比较基准 $aircraft 不可用：${state.reason}"
        else -> (session.parameterResult?.exceptionOrNull() ?: session.detailResult?.exceptionOrNull())?.let {
            "比较基准 $aircraft 计算失败：${it.message}"
        } ?: "正在从本地文件恢复比较基准 $aircraft…"
    }
    Text(message, Modifier.testTag("offline-baseline-status"))
    TextButton(onClick = session.reload) { Text("重新加载比较基准") }
}
