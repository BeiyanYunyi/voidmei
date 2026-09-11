package voidmei.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Match reading shadows while reserving room for their lower/right edge. */
@Composable
internal fun HudOverlayText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current) {
    val shade = LocalReadingColors.current.shade
    val density = LocalDensity.current
    val inset = if (shade == null) 0.dp else with(density) { 1.sp.toDp() }
    val shadow = shade?.let { with(density) { Shadow(it, Offset(inset.toPx(), inset.toPx()), 0f) } }
    Text(text, modifier.padding(end = inset, bottom = inset), color = color, fontSize = fontSize,
        maxLines = maxLines, overflow = overflow, style = style.copy(shadow = shadow))
}
