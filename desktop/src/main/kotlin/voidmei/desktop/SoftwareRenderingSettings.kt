package voidmei.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/** Called before Compose creates any window; explicit launcher settings take priority. */
internal fun configureStartupRenderer(software: Boolean) {
    if (software && System.getenv("SKIKO_RENDER_API") == null && System.getProperty("skiko.renderApi") == null) {
        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
    }
}

@Composable
internal fun SoftwareRenderingSettings(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = enabled, onCheckedChange = onChange)
        Text("软件渲染（重启后生效）")
    }
    Text("关闭后使用平台默认后端。SKIKO_RENDER_API 环境变量和 skiko.renderApi 启动参数优先；HUD 兼容显示是独立设置。")
}
