package voidmei.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Explicit region label settings also govern captions around its engine instruments. */
@Composable
internal fun engineLabelStyle(base: TextStyle): TextStyle = base.copy(
    fontFamily = LocalReadingLabelFont.current ?: base.fontFamily,
    fontSize = LocalReadingTextSizes.current?.label?.sp ?: base.fontSize,
    fontWeight = LocalReadingTextWeights.current?.label?.let(::FontWeight) ?: base.fontWeight)
