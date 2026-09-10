package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.nio.file.Path

@Composable
internal fun VoicePackInstallPanel(directory: String, chooseFile: (String) -> String? = ::chooseVoiceArchive,
    onInstalled: (InstalledVoicePack) -> Unit) {
    var archive by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val installedCallback by rememberUpdatedState(onInstalled)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("安装 ZIP 语音包到：$directory。WAV 按文件名展开，全部校验通过后安装到新目录；安装后可在上方检查并应用。")
        OutlinedTextField(archive, { archive = it; status = null }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("语音 ZIP 路径") }, singleLine = true)
        TextButton(enabled = !busy, onClick = {
            chooseFile(archive)?.let { archive = it; status = null }
        }) { Text("选择 ZIP 语音包") }
        OutlinedTextField(name, { name = it; status = null }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("新语音包目录名") }, singleLine = true)
        Button(enabled = !busy && directory.isNotBlank() && archive.isNotBlank() && name.isNotBlank(), onClick = {
            val selectedArchive = archive
            val selectedName = name
            val selectedRoot = directory
            busy = true
            status = null
            scope.launch {
                try {
                    val installed = withContext(Dispatchers.IO) {
                        val context = currentCoroutineContext()
                        VoicePackInstaller.install(Path.of(selectedArchive), Path.of(selectedRoot), selectedName) { context.ensureActive() }
                    }
                    status = "已安装 ${installed.files} 个 WAV：${installed.directory}"
                    installedCallback(installed)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "安装失败：${e.message}" }
                finally { busy = false }
            }
        }) { Text("校验并安装 ZIP") }
        status?.let { Text(it) }
    }
}
