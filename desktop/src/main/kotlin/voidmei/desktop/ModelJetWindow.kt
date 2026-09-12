package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import voidmei.config.AppSettings
import voidmei.fm.JetThrustModel
import voidmei.telemetry.Telemetry
import voidmei.telemetry.triggersJetWindowDismissal

@Composable
internal fun ModelJetWindow(state: WindowState, settings: AppSettings, models: List<JetThrustModel>, telemetry: Telemetry? = null, sessionKey: Any? = null, onClose: () -> Unit) {
    val active = settings.modelWindowEnabled && settings.modelJetWindowEnabled && models.isNotEmpty()
    val dismissed = rememberJetWindowDismissed(active && settings.modelJetWindowAutoClose && !settings.modelWindowHotkeyEnabled,
        telemetry != null, telemetry?.triggersJetWindowDismissal() == true, sessionKey)
    if (!active || dismissed) return
    Window(onCloseRequest = onClose, title = "VoidMei · 喷气推力", state = state, alwaysOnTop = settings.modelWindowAlwaysOnTop) {
        rememberRendererDiagnostics(window)
        MaterialTheme(typography = textTypography(resolveTextFont(settings.textFont).family)) {
            Surface {
                ModelJetWindowContent(models, onClose)
            }
        }
    }
}

@Composable
internal fun ModelJetWindowContent(models: List<JetThrustModel>, onClose: () -> Unit) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("model-jet-window-close")) { Text("关闭推力窗口联动") }
                    Text("当前模型的独立喷气推力图", style = MaterialTheme.typography.titleLarge)
                    Text("随模型浮窗及其热键显示；各来源按单台发动机展示，使用 FM 节点，不表示当前油门下的实时总推力。")
                    models.forEachIndexed { index, model ->
                        key(index, model) { JetAltitudeComparisonPanel(model, initiallyExpanded = true) }
                    }
                }
}
