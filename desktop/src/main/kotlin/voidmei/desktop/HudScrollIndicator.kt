package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A visual position cue only; wheel scrolling and HUD input handling remain with the body. */
@Composable
internal fun HudScrollIndicator(scroll: ScrollState, modifier: Modifier = Modifier) {
    if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return
    val progress = (scroll.value.toFloat() / scroll.maxValue).coerceIn(0f, 1f)
    val color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0)
    Canvas(modifier.testTag("hud-scroll-indicator").semantics {
        contentDescription = "HUD 读数滚动位置"
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    }) {
        val width = 3.dp.toPx().coerceAtMost(size.width)
        val height = size.height
        if (height <= 0f || width <= 0f) return@Canvas
        val visibleFraction = height / (height + scroll.maxValue)
        val thumbHeight = (height * visibleFraction).coerceIn(16.dp.toPx().coerceAtMost(height), height)
        val left = size.width - width
        drawRoundRect(color.copy(alpha = color.alpha * .2f), Offset(left, 0f), Size(width, height), CornerRadius(width))
        drawRoundRect(color, Offset(left, (height - thumbHeight) * progress), Size(width, thumbHeight), CornerRadius(width))
    }
}
