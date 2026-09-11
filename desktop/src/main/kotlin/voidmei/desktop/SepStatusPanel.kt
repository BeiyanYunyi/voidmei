package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import voidmei.telemetry.ConnectionState
import voidmei.telemetry.FlightCalculator

internal fun sepUnavailableReason(flight: ConnectionState.Flying, intervalMs: Long?): String? {
    if (flight.metrics.specificExcessPowerMps?.isFinite() == true) return null
    val telemetry = flight.telemetry
    val missing = buildList {
        if (telemetry.tasKmh?.let { it.isFinite() && it >= 0 } != true) add("真空速")
        if (telemetry.verticalSpeedMps?.isFinite() != true) add("爬升率")
    }
    if (missing.isNotEmpty()) return "SEP · 缺少${missing.joinToString("、")}"
    if (intervalMs != null && intervalMs > FlightCalculator.MAXIMUM_SAMPLE_GAP_MS)
        return "SEP · 刷新间隔过长，请设为 ${FlightCalculator.MAXIMUM_SAMPLE_GAP_MS / 1000} 秒以内"
    return if (flight.metrics.accelerationMps2?.isFinite() == true) "SEP · 计算结果不可用"
        else "SEP · 等待连续速度采样"
}

@Composable
internal fun SepStatusPanel(flight: ConnectionState.Flying, intervalMs: Long?) {
    val reason = sepUnavailableReason(flight, intervalMs) ?: return
    Text(reason, Modifier.testTag("hud-sep-status"), style = MaterialTheme.typography.bodySmall,
        color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
}
