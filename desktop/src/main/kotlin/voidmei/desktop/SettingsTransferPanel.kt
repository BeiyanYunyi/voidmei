package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.config.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

internal fun readSettingsBackup(path: Path, compatibleDefault: Boolean): AppSettings {
    require(Files.isRegularFile(path)) { "请选择普通设置文件" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(SettingsStore.MAX_BYTES + 1) }
    require(bytes.size <= SettingsStore.MAX_BYTES) { "设置文件超过 1 MiB" }
    val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    return SettingsJson.decodeBackup(text, defaultHudCompatibilityMode = compatibleDefault).also { validateEndpoint(it.endpoint) }
}

internal fun writeSettingsBackup(path: Path, settings: AppSettings) {
    val bytes = SettingsJson.encode(settings, prettyPrint = false).toByteArray(Charsets.UTF_8)
    require(bytes.size <= SettingsStore.MAX_BYTES) { "设置文件超过 1 MiB" }
    exportNewFile(path, bytes)
}

@Composable
internal fun SettingsTransferPanel(settings: AppSettings, canRestore: Boolean, onRestore: (AppSettings) -> Unit,
    chooseImport: (String) -> String? = ::chooseSettingsImport,
    chooseExport: (String) -> String? = ::chooseSettingsExport) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<AppSettings?>(null) }
    var source by remember { mutableStateOf("") }
    FlowRow {
        TextButton(enabled = !busy, modifier = Modifier.testTag("settings-backup"), onClick = {
            try {
                val path = chooseExport("voidmei-settings-backup.json") ?: return@TextButton
                val snapshot = settings
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { writeSettingsBackup(Path.of(path), snapshot) }
                        status = "已备份设置：$path"
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = "备份失败（请选择未存在的文件名）：${e.message}" }
                    finally { busy = false }
                }
            } catch (e: Exception) { status = "无法选择文件：${e.message}" }
        }) { Text("备份 KMP 设置") }
        TextButton(enabled = !busy && canRestore, modifier = Modifier.testTag("settings-restore-read"), onClick = {
            try {
                val path = chooseImport(source) ?: return@TextButton
                imported = null
                status = ""
                busy = true
                scope.launch {
                    try {
                        imported = withContext(Dispatchers.IO) { readSettingsBackup(Path.of(path), settings.hudCompatibilityMode) }
                        source = path
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = "读取失败：${e.message}" }
                    finally { busy = false }
                }
            } catch (e: Exception) { status = "无法选择文件：${e.message}" }
        }) { Text("从 KMP 备份恢复") }
    }
    if (!canRestore) Text("停止录制并排除设置保存错误后，可恢复备份。", style = MaterialTheme.typography.bodySmall)
    if (busy) Text("正在处理设置备份…")
    if (status.isNotEmpty()) Text(status, Modifier.testTag("settings-transfer-status"))
    imported?.let { restored ->
        AlertDialog(onDismissRequest = { imported = null }, title = { Text("确认恢复 KMP 设置") },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("文件：$source")
                Text("替换当前版本支持的全部设置，包括 HUD、字体、颜色、窗口位置、热键与语音。")
                Text("服务器：${restored.endpoint}\n刷新间隔：${restored.pollIntervalMs} ms")
                Text("HUD：${if (restored.hudEnabled) "开启" else "关闭"} · ${if (restored.hudCompatibilityMode) "兼容模式" else "原生模式"}")
                Text("分区：${restored.hudSceneLayout?.regions?.size ?: 0} 个 · 预设：${restored.hudScenePresets.size} 套")
                fun flag(value: Boolean) = if (value) "开启" else "关闭"
                Text("语音：${flag(restored.voiceEnabled)} · HUD 热键：${flag(restored.hudHotkeyEnabled)} · 自动录制：${flag(restored.recordingAutoStart)}")
                Text("模型目录：${restored.fmDataRoot}\n语音目录：${restored.voiceDirectory}\n记录目录：${restored.recordingDirectory}")
                Text("确认后自动保存。备份仅包含设置，不包含模型、语音或 CSV 文件；跨机器恢复时请检查路径和显示器。当前连接保持原地址，点击“连接”切换。软件渲染和启动选项在重启后生效。")
            } },
            confirmButton = { TextButton(enabled = canRestore && !busy, modifier = Modifier.testTag("settings-restore-confirm"), onClick = {
                onRestore(restored)
                imported = null
                status = "已应用备份设置"
            }) { Text("确认恢复备份") } },
            dismissButton = { TextButton(onClick = { imported = null }) { Text("取消") } })
    }
}
