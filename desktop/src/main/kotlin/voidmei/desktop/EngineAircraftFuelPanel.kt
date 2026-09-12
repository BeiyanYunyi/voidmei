package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import voidmei.telemetry.*

/** Aircraft-wide telemetry stays available even when the chosen engine is absent. */
@Composable
internal fun EngineAircraftFuelPanel(flight: ConnectionState.Flying, alerts: List<FlightAlert>, showInstrument: Boolean) {
    val percent = flight.metrics.fuelPercent?.takeIf { it.isFinite() && it >= 0 }
    val warning = if (percent != null) fuelReadingWarning(alerts) else null
    val colors = LocalReadingColors.current
    val color = if (warning != null) colors.warning ?: MaterialTheme.colorScheme.error
        else colors.value ?: MaterialTheme.colorScheme.onSurface
    val value = "${readingNumber(percent, 1)} %"
    Column {
        FlightReadings(listOf("整机燃油余量" to value), compact = true,
            unitRanges = mapOf(0 to (value.lastIndex..value.lastIndex)),
            warningRows = warning?.let { mapOf(0 to it.label) } ?: emptyMap())
        if (percent == null) HudOverlayText("缺少有效整机燃油余量或容量", style = engineLabelStyle(MaterialTheme.typography.bodySmall))
        if (showInstrument && percent != null) LinearProgressIndicator(
            progress = { (percent / 100).coerceIn(0.0, 1.0).toFloat() },
            color = color, trackColor = color.copy(alpha = color.alpha * .2f),
            modifier = Modifier.engineHorizontalDimensions().testTag("hud-engine-aircraft-fuel").semantics {
                contentDescription = "整机燃油余量，满刻度 100%"
                stateDescription = "整机燃油余量 ${readingNumber(percent, 1)}%" + (warning?.let { "，${it.label}" } ?: "")
            })
    }
}
