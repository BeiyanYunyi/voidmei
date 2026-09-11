package voidmei.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.AppSettings
import voidmei.config.HudSceneLayout

private data class DeletedHudPreset(val name: String, val scene: HudSceneLayout, val index: Int)

private data class PreviousHudLayout(val scene: HudSceneLayout?)

@Composable
internal fun HudScenePresetSettings(settings: AppSettings, canLoad: Boolean = true, onLoad: () -> Unit = {}, onChange: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<DeletedHudPreset?>(null) }
    var previous by remember { mutableStateOf<PreviousHudLayout?>(null) }
    var preview by remember { mutableStateOf<Pair<String, HudSceneLayout>?>(null) }
    var previewRequest by remember { mutableStateOf(0) }
    preview?.let { (name, scene) ->
        HudLayoutPreviewWindow(settings.copy(hudSceneLayout = scene.copy(enabled = true)), previewRequest,
            presetName = name, onClose = { preview = null })
    }
    TextButton({ expanded = !expanded }, Modifier.testTag("hud-presets-toggle")) { Text(if (expanded) "收起布局预设" else "管理布局预设") }
    previous?.let { saved ->
        TextButton({
            onLoad()
            onChange(settings.copy(hudSceneLayout = saved.scene))
            previous = null
        }, enabled = saved.scene?.enabled != true || canLoad, modifier = Modifier.testTag("hud-preset-undo-load")) {
            Text("撤销上次载入")
        }
        Text("恢复载入前布局；仅保留本次页面的最近一次载入记录。")
    }
    deleted?.let { saved ->
        val conflict = saved.name in settings.hudScenePresets
        val full = settings.hudScenePresets.size >= 16
        TextButton({
            val entries = settings.hudScenePresets.entries.map { it.key to it.value }.toMutableList()
            entries.add(saved.index.coerceIn(0, entries.size), saved.name to saved.scene)
            onChange(settings.copy(hudScenePresets = entries.toMap()))
            deleted = null
        }, enabled = !conflict && !full, modifier = Modifier.testTag("hud-preset-restore-deleted")) {
            Text("恢复上次删除的预设 · ${saved.name}")
        }
        Text(when {
            conflict -> "已有同名预设，恢复不会覆盖它；可先将同名预设重命名。"
            full -> "已达 16 套预设，暂不能恢复。"
            else -> "仅保留本次页面最近一次删除；再次删除会替换恢复记录。"
        })
    }
    if (!expanded) return
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 80 && trimmed.none { it.isISOControl() }
    val exists = trimmed in settings.hudScenePresets
    Text("保存画布与区域配置；继承的字段、字体和颜色继续使用全局设置。最多 16 套。")
    OutlinedTextField(name, { name = it }, label = { Text("布局名称") }, singleLine = true,
        supportingText = { Text("输入保存名称，或用于下方预设重命名；重命名不会覆盖同名预设。") },
        isError = name.isNotEmpty() && !valid, modifier = Modifier.testTag("hud-preset-name"))
    Button(onClick = {
        settings.hudSceneLayout?.let { scene ->
            onChange(settings.copy(hudScenePresets = settings.hudScenePresets + (trimmed to scene)))
        }
    }, enabled = valid && settings.hudSceneLayout != null && (exists || settings.hudScenePresets.size < 16),
        modifier = Modifier.testTag("hud-preset-save")) { Text(if (exists) "替换同名布局" else "保存当前分区布局") }
    settings.hudScenePresets.forEach { (key, scene) ->
        Text("$key · ${scene.regions.size} 区域 · ${scene.width} × ${scene.height} dp")
        FlowRow {
            TextButton({ preview = key to scene; previewRequest++ },
                modifier = Modifier.testTag("hud-preset-preview-$key")) { Text("预览") }
            TextButton({
                previous = PreviousHudLayout(settings.hudSceneLayout)
                onLoad()
                onChange(settings.copy(hudSceneLayout = scene.copy(enabled = true)))
            }, enabled = canLoad,
                modifier = Modifier.testTag("hud-preset-load-$key")) { Text("载入") }
            TextButton({
                onChange(settings.copy(hudScenePresets = settings.hudScenePresets.entries.associate { (oldName, layout) ->
                    (if (oldName == key) trimmed else oldName) to layout
                }))
            }, enabled = valid && !exists, modifier = Modifier.testTag("hud-preset-rename-$key")) {
                Text("重命名为输入名称")
            }
            TextButton({
                deleted = DeletedHudPreset(key, scene, settings.hudScenePresets.keys.indexOf(key))
                onChange(settings.copy(hudScenePresets = settings.hudScenePresets - key))
            },
                Modifier.testTag("hud-preset-delete-$key")) { Text("删除预设") }
        }
    }
}
