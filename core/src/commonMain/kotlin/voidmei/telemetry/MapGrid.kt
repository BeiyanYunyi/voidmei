package voidmei.telemetry

import kotlin.math.floor

/** Rows run south from gridZero.y; columns run east from gridZero.x. */
data class MapGridLines(val vertical: List<Double>, val horizontal: List<Double>)

object MapGrid {
    /** Normalized grid boundaries, bounded to prevent malformed metadata from flooding the canvas. */
    fun lines(bounds: MapBounds): MapGridLines? {
        val step = bounds.gridSteps ?: return null
        val zero = bounds.gridZero ?: return null
        fun axis(origin: Double, stepM: Double, extent: Double): List<Double>? {
            if (!origin.isFinite() || !stepM.isFinite() || !extent.isFinite() || stepM <= 0 || extent <= 0) return null
            val spacing = stepM / extent
            val periods = origin / stepM
            if (!spacing.isFinite() || spacing <= 0 || !periods.isFinite() || kotlin.math.abs(periods) > 1e12 ||
                1 / spacing > 255) return null
            // Reduce in source units: dividing both operands first can turn an exact
            // whole-cell shift into 2.999999..., dropping the first boundary.
            val remainder = origin % stepM
            val firstM = if (remainder < 0) remainder + stepM else remainder
            return (0..255).map { (firstM + it * stepM) / extent }.takeWhile { it <= 1.0 }
        }
        return MapGridLines(
            axis(zero.x - bounds.minimum.x, step.x, bounds.widthM) ?: return null,
            axis(bounds.maximum.y - zero.y, step.y, bounds.heightM) ?: return null)
    }

    fun playerCell(snapshot: MapSnapshot): String? {
        val position = snapshot.player?.position ?: return null
        val bounds = snapshot.bounds
        val step = bounds.gridSteps ?: return null
        val zero = bounds.gridZero ?: return null
        if (!position.x.isFinite() || !position.y.isFinite() ||
            position.x !in 0.0..1.0 || position.y !in 0.0..1.0 ||
            !step.x.isFinite() || !step.y.isFinite() || step.x <= 0 || step.y <= 0 ||
            !zero.x.isFinite() || !zero.y.isFinite() ||
            !bounds.widthM.isFinite() || !bounds.heightM.isFinite() || bounds.widthM <= 0 || bounds.heightM <= 0) return null
        val column = floor((bounds.minimum.x + position.x * bounds.widthM - zero.x) / step.x)
        val row = floor((zero.y - bounds.maximum.y + position.y * bounds.heightM) / step.y)
        // The legacy display uses one letter. Do not invent labels outside that range.
        if (row !in 0.0..25.0 || column !in 0.0..999.0) return null
        return "${'A' + row.toInt()}${column.toInt() + 1}"
    }
}
