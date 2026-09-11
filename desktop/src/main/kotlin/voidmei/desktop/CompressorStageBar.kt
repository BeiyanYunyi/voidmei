package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import voidmei.telemetry.*

/** FM NumSteps is a physical stage count, independent of the selected power setting. */
@Composable
internal fun CompressorStageBar(flight: ConnectionState.Flying, index: Int, model: AircraftAlertModel?, fields: List<String>) {
    if (HudEngineField.COMPRESSOR.id !in fields) return
    val compressors = model?.parametersFor(flight.telemetry.aircraft)?.engineCompressors.orEmpty()
    val count = compressors.get(index)
        ?.military?.stages?.size?.takeIf { it in 2..32 } ?: return
    val engine = flight.telemetry.engines.singleOrNull { it.index == index } ?: return
    val stage = HudEngineField.COMPRESSOR.value(engine)?.takeIf { it <= count }?.toInt() ?: return
    val recommended = CompressorAdvice.recommendations(flight.telemetry, compressors)
        .singleOrNull { it.engineIndex == index }?.recommendedStage?.takeIf { it in 1..count }
    val label = "$index 号增压器 · $stage / $count 档" +
        (recommended?.let { " · 建议 $it 档（长线）" } ?: "")
    Text(label, style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
    val track = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.primary
    val recommendationMarker = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(20.dp).testTag("hud-compressor-stage-$index").semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(stage.toFloat(), 1f..count.toFloat(), count - 2)
        if (recommended != null) stateDescription = "模型建议 $recommended 档；圆点为当前档位，长线为建议档位"
    }) {
        val inset = minOf(5.dp.toPx(), size.width / 2)
        fun x(position: Int) = inset + (size.width - 2 * inset) * (position - 1) / (count - 1)
        val middle = size.height / 2
        drawLine(track, Offset(x(1), middle), Offset(x(count), middle), 2.dp.toPx())
        for (position in 1..count) drawLine(track, Offset(x(position), middle - 4.dp.toPx()),
            Offset(x(position), middle + 4.dp.toPx()), 1.dp.toPx())
        recommended?.let { drawLine(recommendationMarker, Offset(x(it), 2.dp.toPx()),
            Offset(x(it), size.height - 2.dp.toPx()), 3.dp.toPx()) }
        drawCircle(marker, 4.dp.toPx(), Offset(x(stage), middle))
    }
}
