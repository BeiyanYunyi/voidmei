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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ReadingColors(val label: Color? = null, val value: Color? = null, val warning: Color? = null, val shade: Color? = null, val unit: Color? = null)
internal val LocalReadingNumberFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
internal val LocalReadingColors = androidx.compose.runtime.staticCompositionLocalOf { ReadingColors() }

/** Keep both the label and its reading visible as width, font scale and values change. */
@Composable
internal fun FlightReadings(rows: List<Pair<String, String>>, compact: Boolean, warningRows: Map<Int, String> = emptyMap(), hiddenLabels: Set<Int> = emptySet(), unitRanges: Map<Int, IntRange> = emptyMap()) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = LocalReadingColors.current
    val shadeInset = if (colors.shade != null) with(density) { 1.sp.toDp() } else 0.dp
    val shadow = colors.shade?.let { color ->
        val offset = with(density) { shadeInset.toPx() }
        androidx.compose.ui.graphics.Shadow(color, androidx.compose.ui.geometry.Offset(offset, offset), 0f)
    }
    val shadePadding = Modifier.padding(end = shadeInset, bottom = shadeInset)
    val labelStyle = LocalTextStyle.current.copy(color = colors.label ?: Color(0xFF9EB1C0), fontSize = if (compact) 13.sp else 16.sp, shadow = shadow)
    val valueStyle = LocalTextStyle.current.copy(color = colors.value ?: Color.White, fontFamily = LocalReadingNumberFont.current,
        fontSize = if (compact) 14.sp else 20.sp, shadow = shadow)
    val widths = rows.mapIndexed { index, (label, value) ->
        with(density) {
            (measurer.measure(if (index in hiddenLabels) "" else label, labelStyle, softWrap = false).size.width +
                measurer.measure(value, valueStyle, softWrap = false).size.width).toDp() + 8.dp + shadeInset * (if (index in hiddenLabels) 1 else 2)
        }
    }
    val measuredWidth = widths.maxOrNull() ?: 0.dp
    var widestReading by remember(rows.map { it.first }, compact, hiddenLabels, density, shadeInset,
        labelStyle.fontFamily, valueStyle.fontFamily) { mutableStateOf(0.dp) }
    val layoutWidth = if (compact) maxOf(widestReading, measuredWidth) else measuredWidth
    // Telemetry getting shorter must not shuffle HUD fields back into another column.
    // Growth is still measured immediately, and explicit layout/font changes reset the budget.
    SideEffect { if (widestReading != layoutWidth) widestReading = layoutWidth }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (layoutWidth * 2 + 20.dp <= maxWidth) 2 else 1
        val cellWidth = (maxWidth - 20.dp * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp)) {
            rows.indices.toList().chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    indices.forEach { index ->
                        val (label, value) = rows[index]
                        val readingStyle = if (index in warningRows) valueStyle.copy(color = colors.warning ?: MaterialTheme.colorScheme.error) else valueStyle
                        val readingText = androidx.compose.ui.text.buildAnnotatedString {
                            append(value)
                            val range = unitRanges[index]
                            if (colors.unit != null && range != null && !range.isEmpty() && range.first >= 0 && range.last < value.length)
                                addStyle(androidx.compose.ui.text.SpanStyle(color = colors.unit), range.first, range.last + 1)
                        }
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
