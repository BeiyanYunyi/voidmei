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

@Composable
fun AlertVoicePanel(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("逐条语音设置") }
    if (!expanded) return
    Text("关闭播报后仍显示屏幕告警。包名留空使用全局语音包，default 使用根目录及内置语音。")
    FlightAlert.entries.forEach { alert ->
        val choice = settings.alertVoices[alert.voice] ?: VoiceChoice()
        var pack by remember(choice.pack) { mutableStateOf(choice.pack.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alert.label)
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
