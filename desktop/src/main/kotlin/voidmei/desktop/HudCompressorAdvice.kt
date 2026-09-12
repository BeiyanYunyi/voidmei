package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.*

@Composable
internal fun HudCompressorAdvice(flight: ConnectionState.Flying, index: Int, model: AircraftAlertModel?, fields: List<String>) {
    if (HudEngineField.COMPRESSOR.id !in fields) return
    val parameters = model?.parametersFor(flight.telemetry.aircraft) ?: return
    val advice = CompressorAdvice.recommendations(flight.telemetry, parameters.engineCompressors)
        .singleOrNull { it.engineIndex == index } ?: return
    val fuel = parameters.compressorFuel?.let { fuelName(it.id) } ?: "基础燃油"
    Text("#$index 增压器 ${advice.actualStage} → ${advice.recommendedStage}（$fuel、15°C 模型估算）",
        Modifier.testTag("hud-compressor-advice-$index"), style = engineLabelStyle(MaterialTheme.typography.bodySmall),
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
