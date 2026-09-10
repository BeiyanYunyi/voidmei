package voidmei.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.ComposePanel
import org.jetbrains.skiko.SkiaLayer
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
    return buildString {
        append("绘制后端：$api")
        if (details.isNotEmpty()) append("\n$details")
    }
}
