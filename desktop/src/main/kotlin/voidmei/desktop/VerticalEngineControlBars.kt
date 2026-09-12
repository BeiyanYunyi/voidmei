package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voidmei.telemetry.*

@Composable
internal fun VerticalEngineControlBars(engine: Engine, fields: List<HudEngineField>, powerPercent: PowerPercentReading?) {
    val dimensions = LocalEngineControlDimensions.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = minOf(maxWidth, 140.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.distinct().forEach { field ->
                val maximum = when (field) {
                    HudEngineField.THROTTLE -> 110
                    HudEngineField.MIXTURE -> 120
                    HudEngineField.RPM_CONTROL, HudEngineField.RADIATOR, HudEngineField.OIL_RADIATOR, HudEngineField.FM_POWER_PERCENT -> 100
                    else -> return@forEach
                }
                val value = (if (field == HudEngineField.FM_POWER_PERCENT) powerPercent?.percent else field.value(engine))
                    ?.takeIf { it.isFinite() && it >= 0 } ?: return@forEach
                val fraction = (value / maximum).coerceIn(0.0, 1.0).toFloat()
                val label = "${engine.index} 号${field.label}，满刻度 $maximum%"
                val fill = if (field == HudEngineField.THROTTLE && value > 100) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.surfaceVariant
                Column(Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(field.label, style = engineLabelStyle(MaterialTheme.typography.bodySmall),
                        color = LocalReadingColors.current.label ?: LocalContentColor.current)
                    Text("${readingNumber(value, field.decimals)} %", style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = LocalReadingNumberFont.current ?: MaterialTheme.typography.bodySmall.fontFamily,
                        fontSize = LocalReadingTextSizes.current?.number?.sp ?: 14.sp,
                        fontWeight = LocalReadingTextWeights.current?.number?.let(::FontWeight) ?: MaterialTheme.typography.bodySmall.fontWeight),
                        color = LocalReadingColors.current.value ?: LocalContentColor.current)
                    Canvas(Modifier.size((dimensions?.thicknessDp ?: 18).dp, (dimensions?.lengthDp ?: 112).dp).testTag("hud-engine-${field.id}-${engine.index}").semantics {
                        contentDescription = label
                        stateDescription = "竖向控制条；实际 ${readingNumber(value, field.decimals)}%；满刻度 $maximum%"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    }) {
                        drawRect(track)
                        val height = size.height * fraction
                        drawRect(fill, Offset(0f, size.height - height), Size(size.width, height))
                    }
                    Text("0–$maximum%", style = engineLabelStyle(MaterialTheme.typography.bodySmall))
                    if (field == HudEngineField.FM_POWER_PERCENT) Text(powerPercent!!.source,
                        style = engineLabelStyle(MaterialTheme.typography.bodySmall))
                }
            }
        }
    }
}
