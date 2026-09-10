package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import voidmei.config.*
import voidmei.telemetry.FlightAlert
import kotlinx.coroutines.*

@Composable
fun AlertVoicePanel(settings: AppSettings, onPreview: (suspend (FlightAlert) -> Unit)? = null,
    onStopPreview: () -> Unit = {}, onSettings: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("逐条语音设置") }
    if (!expanded) return
    DisposableEffect(Unit) { onDispose { onStopPreview() } }
    val scope = rememberCoroutineScope()
    var previewBusy by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    if (onPreview != null) {
        Text("试听使用已应用的语音包与音量，不改变播报开关。告警优先。")
        TextButton(onClick = { previewJob?.cancel(); onStopPreview() }) { Text("停止试听") }
        previewError?.let { Text("试听失败：$it", color = MaterialTheme.colorScheme.error) }
    }
    Text("关闭播报后仍显示屏幕告警。包名留空使用全局语音包，default 使用根目录及内置语音。")
    Text("连接成功提示音默认关闭；开启后在进入有效飞行时播放，告警优先。短时间重连不重复播放。")
    FlightAlert.entries.forEach { alert ->
        val choice = settings.alertVoices[alert.voice] ?: VoiceChoice(enabled = alert != FlightAlert.CONNECTION_READY)
        var pack by remember(choice.pack) { mutableStateOf(choice.pack.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alert.label)
                if (onPreview != null) TextButton(enabled = !previewBusy && settings.voiceVolume > 0, onClick = {
                    previewBusy = true; previewError = null
                    previewJob = scope.launch {
                        try { onPreview(alert) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { previewError = e.message ?: "音频不可用" }
                        finally { previewBusy = false }
                    }
                }) { Text("试听${alert.label}") }
                Switch(choice.enabled, { enabled ->
                    onSettings(settings.copy(alertVoices = settings.alertVoices + (alert.voice to choice.copy(enabled = enabled))))
                }, Modifier.semantics { contentDescription = "${alert.label}播报" })
            }
            OutlinedTextField(pack, { pack = it; error = null }, Modifier.fillMaxWidth(),
                label = { Text("${alert.label}语音包") }, singleLine = true)
            TextButton(onClick = {
                try {
                    val next = choice.copy(pack = pack.takeIf { it.isNotEmpty() })
                    onSettings(settings.copy(alertVoices = settings.alertVoices + (alert.voice to next)))
                    error = null
                } catch (e: IllegalArgumentException) { error = e.message }
            }) { Text("应用${alert.label}语音包") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
