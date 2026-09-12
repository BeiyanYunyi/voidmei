package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.nio.file.Path
import voidmei.config.*

@Composable
internal fun LegacySettingsImport(settings: AppSettings,
    chooseFile: (String) -> String? = ::chooseLegacySettingsFile,
    readSettings: (Path, Path?) -> LegacySettings = ::readLegacySettings,
    onChange: (AppSettings) -> Unit) {
    var undo by remember { mutableStateOf<SettingsUndo?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LegacySettingsPanel(settings.hudSceneLayout, chooseFile, readSettings) { imported ->
        val updated = imported.applyTo(settings)
        val change = SettingsUndo.capture(settings, updated)
        onChange(updated)
        if (change != null) undo = change
        status = null
    }
    undo?.let { action ->
        val preview = remember(action, settings) { action.preview(settings) }
        TextButton(enabled = preview.restoredKeys.isNotEmpty(), onClick = {
            try {
                onChange(preview.settings)
                undo = null
                status = "已撤回 ${preview.restoredKeys.size} 项，保留 ${preview.skippedKeys.size} 项当前修改。"
            } catch (e: IllegalArgumentException) { status = "撤回失败：${e.message}" }
        }) { Text("撤回上次旧设置导入") }
        Text("可恢复 ${preview.restoredKeys.size} 项；${preview.skippedKeys.size} 项当前值已不同于导入值，将保留。")
        Text("仅恢复仍为导入值的设置；字段列表、配色表、语音选择和整套分区布局各按整项比较。本次页面只保留最近一次有变化的导入，重启后清除。",
            style = MaterialTheme.typography.bodySmall)
    }
    status?.let { Text(it) }
}
