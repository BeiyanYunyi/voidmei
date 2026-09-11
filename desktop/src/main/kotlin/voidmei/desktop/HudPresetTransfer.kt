package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*
import voidmei.config.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

internal fun readHudPresets(path: Path): Map<String, HudSceneLayout> {
    require(Files.isRegularFile(path)) { "请选择普通预设文件" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(HudPresetFile.MAX_BYTES + 1) }
    require(bytes.size <= HudPresetFile.MAX_BYTES) { "预设文件超过 1 MiB" }
    return HudPresetFile.decode(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
}

internal fun writeHudPresets(path: Path, presets: Map<String, HudSceneLayout>) {
    val bytes = HudPresetFile.encode(presets).toByteArray(Charsets.UTF_8)
    require(bytes.size <= HudPresetFile.MAX_BYTES) { "预设文件超过 1 MiB" }
    exportNewFile(path, bytes)
}

@Composable
internal fun HudPresetTransfer(settings: AppSettings, onChange: (AppSettings) -> Unit,
    chooseImport: (String) -> String? = ::chooseHudPresetImport,
    chooseExport: (String) -> String? = ::chooseHudPresetExport) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<Map<String, HudSceneLayout>?>(null) }
    var source by remember { mutableStateOf("") }
    TextButton({
        try {
            val path = chooseExport("voidmei-hud-presets.json") ?: return@TextButton
            val snapshot = settings.hudScenePresets.toMap()
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { writeHudPresets(Path.of(path), snapshot) }
                    status = "已导出 ${snapshot.size} 套预设：$path"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "导出失败（请选择未存在的文件名）：${e.message}" }
                finally { busy = false }
            }
        } catch (e: Exception) { status = "无法选择文件：${e.message}" }
    }, enabled = !busy && settings.hudScenePresets.isNotEmpty(), modifier = Modifier.testTag("hud-presets-export")) {
        Text("导出全部预设")
    }
    TextButton({
        try {
            val path = chooseImport(source) ?: return@TextButton
            imported = null
            status = ""
            busy = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { readHudPresets(Path.of(path)) }
                    imported = result
                    source = path
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = "读取失败：${e.message}" }
                finally { busy = false }
            }
        } catch (e: Exception) { status = "无法选择文件：${e.message}" }
    }, enabled = !busy, modifier = Modifier.testTag("hud-presets-import")) { Text("读取预设文件") }
    if (busy) Text("正在处理预设文件…")
    if (status.isNotEmpty()) Text(status, Modifier.testTag("hud-presets-file-status"))
    imported?.let { presets ->
        val additions = presets.filterKeys { it !in settings.hudScenePresets }
        val fits = settings.hudScenePresets.size + additions.size <= 16
        Text("文件：$source")
        if (presets.isEmpty()) Text("文件中没有预设。")
        presets.forEach { (name, scene) ->
            Text("$name · ${scene.regions.size} 区域 · ${scene.width} × ${scene.height} dp" +
                if (name in settings.hudScenePresets) " · 同名，跳过" else " · 待添加")
        }
        Text("仅添加不同名预设，保留当前布局与全局设置。")
        if (!fits) Text("添加后超过 16 套，请先移除部分已保存预设。")
        Button({
            onChange(settings.copy(hudScenePresets = settings.hudScenePresets + additions))
            imported = null
            status = "已添加 ${additions.size} 套预设；可在列表中预览或载入。"
        }, enabled = !busy && additions.isNotEmpty() && fits, modifier = Modifier.testTag("hud-presets-import-apply")) {
            Text("添加 ${additions.size} 套预设")
        }
        TextButton({ imported = null }, enabled = !busy) { Text("取消导入") }
    }
}
