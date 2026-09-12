package voidmei.config

import kotlin.math.roundToInt

/** Screen coordinate dimensions used by Java Toolkit when the legacy layout was loaded. */
data class LegacyScreenSize(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
}

/** Legacy values greater than 2 are pixels; other values are screen-relative ratios. */
data class LegacyHudPosition(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) }
    val needsScreenSize: Boolean get() = x > 2.0 || y > 2.0

    fun normalized(screenSize: LegacyScreenSize?): LegacyHudPosition {
        require(!needsScreenSize || screenSize != null) { "旧像素坐标需要原屏幕宽度和高度" }
        return LegacyHudPosition(if (x > 2.0) x / screenSize!!.width else x,
            if (y > 2.0) y / screenSize!!.height else y)
    }
}

/** Move the first matching region; optionally append missing types with Kotlin defaults. */
fun HudSceneLayout.withLegacyPositions(positions: Map<HudRegionContent, LegacyHudPosition>,
    createMissing: Boolean = false, screenSize: LegacyScreenSize? = null): HudSceneLayout {
    val missing = positions.keys - regions.map { it.content }.toSet()
    require(!createMissing || regions.size + missing.size <= 32) { "补建后超过 32 个分区，请先删除不需要的分区" }
    val scene = if (createMissing) missing.fold(this) { layout, content -> layout.addRegion(content) } else this
    val remaining = positions.toMutableMap()
    return scene.copy(regions = scene.regions.map { region ->
        val position = remaining.remove(region.content) ?: return@map region
        region.withLegacyPosition(position, width, height, screenSize)
    })
}

/** Apply independent legacy overlay switches to the first region of each matching type. */
fun HudSceneLayout.withLegacyVisibility(visibility: Map<HudRegionContent, Boolean>): HudSceneLayout {
    val remaining = visibility.toMutableMap()
    return copy(regions = regions.map { region ->
        val visible = remaining.remove(region.content) ?: return@map region
        region.copy(visible = visible)
    })
}

/** The same clamping and per-axis conversion for explicitly chosen engine regions. */
fun HudRegion.withLegacyPosition(position: LegacyHudPosition, canvasWidth: Int, canvasHeight: Int,
    screenSize: LegacyScreenSize?): HudRegion {
    val normalized = position.normalized(screenSize)
    return copy(
        x = (normalized.x.coerceIn(0.0, 1.0) * canvasWidth).roundToInt().coerceAtMost(canvasWidth - width),
        y = (normalized.y.coerceIn(0.0, 1.0) * canvasHeight).roundToInt().coerceAtMost(canvasHeight - height))
}
