package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import voidmei.config.AppSettings
import voidmei.telemetry.Telemetry

@Composable
internal fun ModelWindowControls(settings: AppSettings, enabled: Boolean = true, onChange: (AppSettings) -> Unit) {
    Row {
        Switch(settings.modelWindowEnabled, { onChange(settings.copy(modelWindowEnabled = it)) }, enabled = enabled,
            modifier = Modifier.testTag("model-window-enabled"))
        Text("独立模型浮窗")
        Switch(settings.modelWindowAlwaysOnTop, { onChange(settings.copy(modelWindowAlwaysOnTop = it)) }, enabled = enabled,
            modifier = Modifier.testTag("model-window-on-top"))
        Text("浮窗置顶")
    }
    Row {
        Switch(settings.modelWindowHotkeyEnabled, { onChange(settings.copy(modelWindowHotkeyEnabled = it)) }, enabled = enabled,
            modifier = Modifier.testTag("model-window-hotkey"))
        Text("${voidmei.config.ModelHotkey.parse(settings.modelWindowHotkey).display} 切换模型浮窗")
    }
    Row {
        Switch(settings.modelJetWindowEnabled, { onChange(settings.copy(modelJetWindowEnabled = it)) }, enabled = enabled,
            modifier = Modifier.testTag("model-jet-window-enabled"))
        Text("联动独立喷气推力窗口")
    }
    Text("仅在模型浮窗开启且有喷气模型时显示，跟随模型热键；关闭推力窗口只取消此联动。", style = MaterialTheme.typography.bodySmall)
    ModelHotkeyEditor(settings, enabled, onChange)
    Text("与主窗口共享当前机型、燃油方案和分类选择；关闭浮窗不退出应用。开启状态与位置会保存。", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun ModelFloatingWindow(state: WindowState, settings: AppSettings, telemetry: Telemetry?, session: FlightModelSession,
    onClose: () -> Unit, onChange: (AppSettings) -> Unit) {
    Window(onCloseRequest = onClose, title = "VoidMei · 当前模型", state = state, alwaysOnTop = settings.modelWindowAlwaysOnTop) {
        MaterialTheme(typography = textTypography(resolveTextFont(settings.textFont).family)) {
            Surface {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("model-window-close")) { Text("关闭模型浮窗") }
                    if (telemetry == null) Text("当前没有实时遥测；暂时延迟时保留模型静态信息，实时条件显示未知。")
                    FlightModelPanel(telemetry, settings.fmDataRoot, onModel = { _, _ -> }, session = session,
                        showDirectoryControls = false, onDataRoot = {}, hiddenSections = settings.hiddenModelSections,
                        onHiddenSections = { onChange(settings.copy(hiddenModelSections = it)) })
                }
            }
        }
    }
}
