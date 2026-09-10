package voidmei.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.ComposePanel
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoProperties
import java.awt.Component
import java.awt.Container

/** Inspect the initialized renderer, rather than treating an OpenGL preference as proof of GPU use. */
internal fun rendererDiagnostics(window: java.awt.Window): String {
    fun findLayer(component: Component): SkiaLayer? {
        if (component is SkiaLayer) return component
        if (component is Container) component.components.forEach { findLayer(it)?.let { layer -> return layer } }
        return null
    }
    val layer = findLayer(window)
    fun findPanel(component: Component): ComposePanel? {
        if (component is ComposePanel) return component
        if (component is Container) component.components.forEach { findPanel(it)?.let { panel -> return panel } }
        return null
    }
    val api = (window as? ComposeWindow)?.renderApi ?: findPanel(window)?.renderApi
    val details = layer?.let { runCatching { it.renderInfo }.getOrNull() }?.trim().orEmpty()
    val transform = window.graphicsConfiguration?.defaultTransform
    return formatRendererDiagnostics(api?.name, SkikoProperties.renderApi.name,
        if (layer != null) "SkiaLayer" else if (findPanel(window) != null) "SwingGraphics" else "未知",
        transform?.scaleX, transform?.scaleY, details)
}

internal fun formatRendererDiagnostics(actual: String?, requested: String, presentation: String,
    scaleX: Double?, scaleY: Double?, details: String): String = buildString {
    append("绘制后端：${actual ?: "等待初始化"}")
    append("\n请求后端：$requested")
    if (actual != null && actual != requested) append("（与实际后端不同）")
    append("\n显示路径：$presentation")
    if (scaleX != null && scaleY != null) append("\n显示缩放：$scaleX × $scaleY")
    if (details.isNotBlank()) append("\n${details.trim()}")
    else append("\n设备信息：当前显示路径未提供")
    if (actual == "OPENGL") append("\nOpenGL 后端也可能使用软件驱动，不能单凭后端名称判断硬件加速。")
}
