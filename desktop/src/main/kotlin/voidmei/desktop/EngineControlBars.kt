package voidmei.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import voidmei.telemetry.Engine
import voidmei.telemetry.HudEngineField
import voidmei.telemetry.PowerPercentReading

/** Legacy control scales; RPM control is a percentage, not the propeller blade angle. */
@Composable
internal fun EngineControlBars(engine: Engine, fields: List<HudEngineField>, powerPercent: PowerPercentReading? = null) {
    fields.distinct().forEach { field ->
        val maximum = when (field) {
            HudEngineField.RPM_CONTROL, HudEngineField.RADIATOR, HudEngineField.OIL_RADIATOR -> 100
            HudEngineField.FM_POWER_PERCENT -> 100
            HudEngineField.MIXTURE -> 120
            else -> return@forEach
        }
        val value = (if (field == HudEngineField.FM_POWER_PERCENT) powerPercent?.percent else field.value(engine))
            ?.takeIf { it.isFinite() && it >= 0 } ?: return@forEach
        val caption = if (field == HudEngineField.FM_POWER_PERCENT) "动力量 · ${powerPercent!!.source}" else field.label
        val label = "${engine.index} 号$caption，满刻度 $maximum%"
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = LocalReadingColors.current.label ?: LocalContentColor.current)
        LinearProgressIndicator(
            progress = { (value / maximum).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().testTag("hud-engine-${field.id}-${engine.index}").semantics {
                contentDescription = label
            })
    }
}
