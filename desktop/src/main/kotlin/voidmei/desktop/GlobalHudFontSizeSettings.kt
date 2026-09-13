package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun GlobalHudFontSizeSettings(scale: Float, onChange: (Float) -> Unit) {
    Text("全局 HUD 文字大小 ${fontScalePercent(scale)}%")
    Slider(value = scale, onValueChange = onChange, valueRange = 0.75f..2f, steps = 24,
        modifier = Modifier.testTag("hud-font-scale").semantics { contentDescription = "全局 HUD 文字大小" })
    HudFontScaleInput(scale, scale, "hud-font-exact", onChange)
    Text("作为 HUD 的默认字号，在系统字体缩放基础上调整。分区选择“继承全局”时使用此值；单独设置字号的分区不受影响。主界面字号沿用系统设置。",
        style = MaterialTheme.typography.bodySmall)
}
