package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import voidmei.config.AppSettings
import voidmei.telemetry.FlightAlert
import java.nio.file.Path

@Composable
fun VoicePackPanel(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("自定义语音包 · ${settings.voicePack}") }
    if (!expanded) return
    var directory by remember(settings.voiceDirectory) { mutableStateOf(settings.voiceDirectory) }
    var pack by remember(settings.voicePack) { mutableStateOf(settings.voicePack) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentSettings by rememberUpdatedState(settings)
    val applySettings by rememberUpdatedState(onSettings)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("沿用旧版 voice/包名/告警名.wav。缺少文件时依次使用 voice 根目录和内置语音；default 使用根目录。每个文件最多 8 MiB、30 秒，告警更新可能提前中断较长语音。")
        OutlinedTextField(directory, { directory = it; status = null }, Modifier.fillMaxWidth(), label = { Text("语音根目录") }, enabled = !busy, singleLine = true)
        OutlinedTextField(pack, { pack = it; status = null }, Modifier.fillMaxWidth(), label = { Text("语音包目录名") }, enabled = !busy, singleLine = true)
        Button(enabled = !busy, onClick = {
            val selectedDirectory = directory
            val selectedPack = pack
            busy = true
            status = null
            scope.launch {
                try {
                    // Validate every currently supported warning before changing active settings.
                    val selection = AppSettings(voiceDirectory = selectedDirectory, voicePack = selectedPack)
                    withContext(Dispatchers.IO) {
                        val resources = VoiceResources(Path.of(selection.voiceDirectory), selection.voicePack)
                        FlightAlert.entries.forEach { resources.open(it).close() }
                    }
                    applySettings(currentSettings.copy(voiceDirectory = selectedDirectory, voicePack = selectedPack))
                    status = "已应用语音包（含缺失文件的默认回退）"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "语音包读取失败：${e.message}" }
                finally { busy = false }
            }
        }) { Text("检查并应用语音包") }
        Text("当前告警文件：" + FlightAlert.entries.joinToString { "${it.voice}.wav" })
        status?.let { Text(it) }
        VoicePackInstallPanel(directory) { installed ->
            directory = installed.directory.parent.toString()
            pack = installed.directory.fileName.toString()
            status = "新语音包已填入，点击检查并应用后启用"
        }
    }
}
