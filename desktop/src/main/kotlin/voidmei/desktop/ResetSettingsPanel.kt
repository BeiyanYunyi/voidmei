package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voidmei.config.AppSettings

@Composable
internal fun ResetSettingsPanel(defaults: AppSettings, enabled: Boolean = true, recording: Boolean = false,
    onReset: (AppSettings) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    TextButton(enabled = enabled && !recording, onClick = { confirming = true }) { Text("恢复默认设置") }
    if (recording) Text("请先停止记录再恢复设置。", style = MaterialTheme.typography.bodySmall)
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("确认恢复默认设置") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("恢复全部设置，包括字体、颜色、HUD 字段、窗口位置、热键和语音选择。")
            Text("遥测服务器：${defaults.endpoint}\n刷新间隔：${defaults.pollIntervalMs} ms")
            Text("HUD、语音告警、热键和启动自动录制恢复为关闭。")
            Text("HUD 绘制模式：${if (defaults.hudCompatibilityMode) "兼容 HUD" else "原生 HUD"}")
            Text("模型目录：${defaults.fmDataRoot}\n语音目录：${defaults.voiceDirectory}\n录制目录：${defaults.recordingDirectory}")
            Text("确认后自动保存。已有模型、语音和 CSV 文件保留。当前遥测连接继续使用原地址；点击“连接”后切换服务器。")
        } },
        confirmButton = { TextButton(enabled = enabled && !recording, onClick = {
            onReset(defaults)
            confirming = false
        }) { Text("确认恢复") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
    )
}
