@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package voidmei.desktop

import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.FontMgr

internal data class HudNumberFont(val family: FontFamily, val unavailable: Boolean)

/** Use the same font manager as Compose, including system font aliases. */
internal fun resolveHudNumberFont(name: String?): HudNumberFont {
    val requested = name?.trim()?.takeIf { it.isNotEmpty() }
        ?: return HudNumberFont(FontFamily.Monospace, false)
    val generic = when (requested.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        else -> null
    }
    if (generic != null) return HudNumberFont(generic, false)
    val available = FontMgr.default.matchFamily(requested).use { it.count() > 0 }
    return if (available) HudNumberFont(FontFamily(requested), false)
        else HudNumberFont(FontFamily.Monospace, true)
}
