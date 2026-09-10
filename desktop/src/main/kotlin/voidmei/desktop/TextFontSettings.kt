package voidmei.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily

internal fun resolveTextFont(name: String?): HudNumberFont {
    val result = resolveHudNumberFont(name)
    return if (name.isNullOrBlank() || result.unavailable) result.copy(family = FontFamily.Default) else result
}

internal fun textTypography(family: FontFamily): Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family), headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family), bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

@Composable
internal fun TextFontSettings(name: String?, onChange: (String?) -> Unit) {
    var draft by remember(name) { mutableStateOf(name.orEmpty()) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    val resolved = remember(name) { resolveTextFont(name) }
    OutlinedTextField(draft, { draft = it; error = null }, singleLine = true, label = { Text("全局文字字体") }, isError = error != null)
    TextButton(onClick = {
        val next = draft.trim().takeIf { it.isNotEmpty() }
        if (next != null && (next.length > 200 || next.any { it.isISOControl() })) error = "字体名称需为不超过 200 字的单行文本"
        else { error = null; onChange(next) }
    }) { Text("应用文字字体") }
    Text("用于界面标题、标签与正文；HUD 专用数字字体单独设置。留空恢复系统默认字体。", style = MaterialTheme.typography.bodySmall)
    if (resolved.unavailable) Text("未找到文字字体 $name，已回退系统默认字体。", color = MaterialTheme.colorScheme.error)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
