package voidmei.config

import kotlin.math.roundToInt

/** Java attitude outer bounds in Toolkit screen coordinates, including its fixed border allowance. */
data class LegacyAttitudeSize(val width: Int = 150, val height: Int = 300, val border: Boolean = false) {
    init { require(width in 100..600 && height in 100..600) }
    fun outerSize(dpiScale: Double): Pair<Int, Int> {
        require(dpiScale.isFinite() && dpiScale > 0 && dpiScale <= 8) { "原 DPI 缩放需大于 0 且不超过 8" }
        val margin = 4 + if (border) 20 else 0
        return (width * dpiScale).roundToInt() + margin to (height * dpiScale).roundToInt() + margin
    }
    fun applyTo(scene: HudSceneLayout, screen: LegacyScreenSize, dpiScale: Double): HudSceneLayout {
        val first = scene.regions.firstOrNull { it.content == HudRegionContent.ATTITUDE } ?: return scene
        val (outerWidth, outerHeight) = outerSize(dpiScale)
        val width = (outerWidth.toDouble() / screen.width * scene.width).roundToInt().coerceIn(80, scene.width)
        val height = (outerHeight.toDouble() / screen.height * scene.height).roundToInt().coerceIn(40, scene.height)
        return scene.copy(regions = scene.regions.map { if (it.id != first.id) it else it.copy(width = width, height = height,
            x = it.x.coerceAtMost(scene.width - width), y = it.y.coerceAtMost(scene.height - height)) })
    }
}
