package voidmei.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import voidmei.fm.PistonPowerPoint
import voidmei.fm.PowerCurveSummary
import java.util.Locale

@Composable
internal fun PowerCurveSummaryPanel(label: String, points: List<PistonPowerPoint?>) {
    val summary = remember(points) { PowerCurveSummary.from(points) }
    val peak = summary.peak ?: return
    Text(String.format(Locale.ROOT, "%s 已知采样峰值 %.2f hp · %.0f m · 增压器档位 %d",
        label, peak.powerHp, peak.altitudeM, peak.stageIndex + 1), style = MaterialTheme.typography.bodySmall)
    summary.transitions.forEach { transition ->
        Text(String.format(Locale.ROOT, "%s 最佳档位变化：%.0f–%.0f m · %d → %d", label,
            transition.fromAltitudeM, transition.toAltitudeM, transition.fromStage + 1, transition.toStage + 1),
            style = MaterialTheme.typography.bodySmall)
    }
    summary.extrema.forEach { extremum ->
        val kind = when (extremum.kind) {
            voidmei.fm.PowerExtremumKind.PEAK -> "局部峰值"
            voidmei.fm.PowerExtremumKind.VALLEY -> "局部谷值"
            voidmei.fm.PowerExtremumKind.KINK -> "斜率转折"
        }
        Text(String.format(Locale.ROOT, "%s %s %.2f hp · %.0f m · 增压器档位 %d", label,
            kind, extremum.point.powerHp, extremum.point.altitudeM, extremum.point.stageIndex + 1),
            style = MaterialTheme.typography.bodySmall)
    }
    if (summary.extrema.isNotEmpty()) Text("局部峰谷采用前后 100 m 邻域、0.5% 显著度和同类 300 m 间距；不跨越缺失点。",
        style = MaterialTheme.typography.bodySmall)
    if (summary.extrema.any { it.kind == voidmei.fm.PowerExtremumKind.KINK })
        Text("斜率转折：前后斜率同向且变化超过连续段平均斜率的 2.5 倍或 0.08 hp/m（取较大值），不表示增压器换档。",
            style = MaterialTheme.typography.bodySmall)
    Text("$label 按 $POWER_CURVE_STEP_M m 采样；峰值仅比较已知点，档位变化区间不跨越缺失数据。", style = MaterialTheme.typography.bodySmall)
}
