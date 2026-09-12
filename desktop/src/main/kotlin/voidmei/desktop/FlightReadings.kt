package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ReadingColors(val label: Color? = null, val value: Color? = null, val warning: Color? = null, val shade: Color? = null, val unit: Color? = null)
internal val LocalReadingTextWeights = staticCompositionLocalOf<voidmei.config.ReadingTextWeights?> { null }
internal val LocalReadingTextSizes = staticCompositionLocalOf<voidmei.config.ReadingTextSizes?> { null }
internal val LocalReadingLabelFont = staticCompositionLocalOf<FontFamily?> { null }
internal val LocalReadingColumns = staticCompositionLocalOf { 0 }
internal val LocalReadingNumberFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
internal val LocalReadingColors = androidx.compose.runtime.staticCompositionLocalOf { ReadingColors() }

/** Keep both the label and its reading visible as width, font scale and values change. */
@Composable
internal fun FlightReadings(rows: List<Pair<String, String>>, compact: Boolean, warningRows: Map<Int, String> = emptyMap(), hiddenLabels: Set<Int> = emptySet(), unitRanges: Map<Int, IntRange> = emptyMap()) {
    val weights = LocalReadingTextWeights.current
    val sizes = LocalReadingTextSizes.current
    val requestedColumns = LocalReadingColumns.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = LocalReadingColors.current
    val shadeInset = if (colors.shade != null) with(density) { 1.sp.toDp() } else 0.dp
    val shadow = colors.shade?.let { color ->
        val offset = with(density) { shadeInset.toPx() }
        androidx.compose.ui.graphics.Shadow(color, androidx.compose.ui.geometry.Offset(offset, offset), 0f)
    }
    val shadePadding = Modifier.padding(end = shadeInset, bottom = shadeInset)
    val labelStyle = LocalTextStyle.current.copy(fontWeight = weights?.label?.let(::FontWeight) ?: LocalTextStyle.current.fontWeight, fontFamily = LocalReadingLabelFont.current ?: LocalTextStyle.current.fontFamily, color = colors.label ?: Color(0xFF9EB1C0), fontSize = sizes?.label?.sp ?: if (compact) 13.sp else 16.sp, shadow = shadow)
    val valueStyle = LocalTextStyle.current.copy(fontWeight = weights?.number?.let(::FontWeight) ?: LocalTextStyle.current.fontWeight, color = colors.value ?: Color.White, fontFamily = LocalReadingNumberFont.current,
        fontSize = sizes?.number?.sp ?: if (compact) 14.sp else 20.sp, shadow = shadow)
    val readingTexts = rows.mapIndexed { index, (_, value) ->
        androidx.compose.ui.text.buildAnnotatedString {
            append(value)
            val range = unitRanges[index]
            if (range != null && !range.isEmpty() && range.first >= 0 && range.last < value.length) {
                if (colors.unit != null) addStyle(androidx.compose.ui.text.SpanStyle(color = colors.unit), range.first, range.last + 1)
                weights?.unit?.let { addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight(it)), range.first, range.last + 1) }
                if (sizes != null) addStyle(androidx.compose.ui.text.SpanStyle(fontSize = sizes.unit.sp), range.first, range.last + 1)
            }
        }
    }
    val widths = rows.mapIndexed { index, (label, value) ->
        with(density) {
            (measurer.measure(if (index in hiddenLabels) "" else label, labelStyle, softWrap = false).size.width +
                measurer.measure(readingTexts[index], valueStyle, softWrap = false).size.width).toDp() + 8.dp + shadeInset * (if (index in hiddenLabels) 1 else 2)
        }
    }
    val measuredWidth = widths.maxOrNull() ?: 0.dp
    var widestReading by remember(rows.map { it.first }, compact, hiddenLabels, density, shadeInset,
        labelStyle.fontFamily, valueStyle.fontFamily, sizes, labelStyle.fontWeight, valueStyle.fontWeight, weights) { mutableStateOf(0.dp) }
    val layoutWidth = if (compact) maxOf(widestReading, measuredWidth) else measuredWidth
    // Telemetry getting shorter must not shuffle HUD fields back into another column.
    // Growth is still measured immediately, and explicit layout/font changes reset the budget.
    SideEffect { if (widestReading != layoutWidth) widestReading = layoutWidth }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (requestedColumns in 1..16) requestedColumns else if (layoutWidth * 2 + 20.dp <= maxWidth) 2 else 1
        val gap = if (columns <= 2) 20.dp else (maxWidth / columns - 40.dp).coerceIn(0.dp, 20.dp)
        val cellWidth = ((maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0.dp)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp)) {
            rows.indices.toList().chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    indices.forEach { index ->
                        val (label, value) = rows[index]
                        val readingStyle = if (index in warningRows) valueStyle.copy(color = colors.warning ?: MaterialTheme.colorScheme.error) else valueStyle
                        val readingText = readingTexts[index]
                        val readingModifier = Modifier.semantics { if (index in hiddenLabels) contentDescription = label
                            warningRows[index]?.let { stateDescription = it } }
                        Box(Modifier.width(cellWidth)) {
                            if (widths[index] <= cellWidth) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (index in hiddenLabels) Arrangement.End else Arrangement.SpaceBetween) {
                                    if (index !in hiddenLabels) Text(label, modifier = shadePadding, style = labelStyle)
                                    Text(readingText, modifier = readingModifier.then(shadePadding), style = readingStyle)
                                }
                            } else {
                                Column {
                                    if (index !in hiddenLabels) Text(label, modifier = shadePadding, style = labelStyle)
                                    Text(readingText, modifier = readingModifier.then(shadePadding), style = readingStyle)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun readingColors(settings: voidmei.config.AppSettings, hud: Boolean = false): ReadingColors {
    fun color(key: String, override: String?) = (override?.takeIf { hud } ?: settings.readingColors[key])
        ?.let { Color(voidmei.config.parseHexColor(it)!!) }
    return ReadingColors(color("label", settings.hudLabelColor), color("value", settings.hudValueColor),
        color("warning", settings.hudWarningColor), color("shade", settings.hudShadeColor), color("unit", settings.hudUnitColor))
}
