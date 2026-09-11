package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import voidmei.telemetry.Engine
import voidmei.telemetry.HudEngineField

/** Legacy control scales; RPM control is a percentage, not the propeller blade angle. */
@Composable
internal fun EngineControlBars(engine: Engine, fields: List<HudEngineField>) {
    fields.distinct().forEach { field ->
        val maximum = when (field) {
            HudEngineField.RPM_CONTROL, HudEngineField.RADIATOR, HudEngineField.OIL_RADIATOR -> 100
            HudEngineField.MIXTURE -> 120
            else -> return@forEach
        }
        val value = field.value(engine) ?: return@forEach
        val label = "${engine.index} 号${field.label}，满刻度 $maximum%"
        Text(label, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (value / maximum).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().testTag("hud-engine-${field.id}-${engine.index}").semantics {
                contentDescription = label
            })
    }
}
