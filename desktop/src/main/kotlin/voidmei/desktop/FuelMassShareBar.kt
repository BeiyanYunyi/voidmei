package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import voidmei.telemetry.*

@Composable
internal fun FuelMassShareBar(flight: ConnectionState.Flying, model: AircraftAlertModel?) {
    val percent = HudField.FUEL_MASS_SHARE.value(flight, model) ?: return
    LinearProgressIndicator(progress = { (percent / 50).coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth().testTag("fuel-mass-share-bar").semantics {
            contentDescription = "燃油质量占比估计，满刻度 50%，不计弹药、外挂和损伤"
        })
}
