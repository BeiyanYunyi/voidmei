package voidmei.telemetry

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class MapScale(val metres: Double, val widthFraction: Double) {
    companion object {
        /** A 1/2/5 distance no longer than one quarter of the map's horizontal extent. */
        fun fromBounds(bounds: MapBounds): MapScale? {
            if (!bounds.widthM.isFinite() || bounds.widthM <= 0 || !bounds.heightM.isFinite() || bounds.heightM <= 0) return null
            val target = bounds.widthM / 4
            val magnitude = 10.0.pow(floor(log10(target)))
            if (!magnitude.isFinite() || magnitude <= 0) return null
            val normalized = target / magnitude
            val metres = (if (normalized >= 5) 5 else if (normalized >= 2) 2 else 1) * magnitude
            val fraction = metres / bounds.widthM
            return if (metres.isFinite() && metres > 0 && fraction.isFinite() && fraction > 0 && fraction <= 0.25)
                MapScale(metres, fraction) else null
        }
    }
}
