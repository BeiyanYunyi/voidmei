package voidmei.config

import kotlin.math.roundToInt

/** Legacy overlays store top-left coordinates relative to the primary screen size. */
data class LegacyHudPosition(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
}

/** Move only the first matching region, retaining its size, contents and visibility. */
fun HudSceneLayout.withLegacyPositions(positions: Map<HudRegionContent, LegacyHudPosition>): HudSceneLayout {
    val remaining = positions.toMutableMap()
    return copy(regions = regions.map { region ->
        val position = remaining.remove(region.content) ?: return@map region
        region.copy(
            x = (position.x.coerceIn(0.0, 1.0) * width).roundToInt().coerceAtMost(width - region.width),
            y = (position.y.coerceIn(0.0, 1.0) * height).roundToInt().coerceAtMost(height - region.height),
        )
    })
}
