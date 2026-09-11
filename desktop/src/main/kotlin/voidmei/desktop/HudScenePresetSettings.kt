package voidmei.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import voidmei.config.AppSettings
import voidmei.config.HudSceneLayout

private data class PreviousHudLayout(val scene: HudSceneLayout?)

@Composable
internal fun HudScenePresetSettings(settings: AppSettings, canLoad: Boolean = true, onLoad: () -> Unit = {}, onChange: (AppSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf<PreviousHudLayout?>(null) }
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
    if (!expanded) return
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 80 && trimmed.none { it.isISOControl() }
    val exists = trimmed in settings.hudScenePresets
    Text("保存画布与区域配置；继承的字段、字体和颜色继续使用全局设置。最多 16 套。")
    OutlinedTextField(name, { name = it }, label = { Text("布局名称") }, singleLine = true,
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
            TextButton({
                previous = PreviousHudLayout(settings.hudSceneLayout)
                onLoad()
                onChange(settings.copy(hudSceneLayout = scene.copy(enabled = true)))
            }, enabled = canLoad,
                modifier = Modifier.testTag("hud-preset-load-$key")) { Text("载入") }
            TextButton({ onChange(settings.copy(hudScenePresets = settings.hudScenePresets - key)) },
                Modifier.testTag("hud-preset-delete-$key")) { Text("删除预设") }
        }
    }
}
