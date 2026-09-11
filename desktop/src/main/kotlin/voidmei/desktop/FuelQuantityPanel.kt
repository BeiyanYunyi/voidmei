package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import voidmei.telemetry.*

internal fun fuelReadingWarning(alerts: List<FlightAlert>): FlightAlert? = when {
    FlightAlert.EMPTY_FUEL in alerts -> FlightAlert.EMPTY_FUEL
    FlightAlert.LOW_FUEL in alerts -> FlightAlert.LOW_FUEL
    else -> null
}

@Composable
internal fun FuelQuantityPanel(flight: ConnectionState.Flying, alerts: List<FlightAlert>) {
    val percent = flight.metrics.fuelPercent?.takeIf { it.isFinite() && it >= 0 } ?: return
    val warning = fuelReadingWarning(alerts)
    val colors = LocalReadingColors.current
    val color = if (warning != null) colors.warning ?: MaterialTheme.colorScheme.error else colors.value ?: Color.White
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("燃油余量 · 满刻度 100%", style = MaterialTheme.typography.bodySmall,
            color = if (warning != null) color else colors.label ?: Color(0xFF9EB1C0))
        LinearProgressIndicator(progress = { (percent / 100).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().testTag("hud-fuel-quantity-bar").semantics {
                contentDescription = "燃油余量，满刻度 100%"
                stateDescription = warning?.label ?: "燃油余量 ${readingNumber(percent)}%"
            }, color = color, trackColor = color.copy(alpha = color.alpha * .2f))
    }
}
