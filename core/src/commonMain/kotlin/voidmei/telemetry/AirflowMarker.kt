package voidmei.telemetry

/** Body-relative legacy AoA/AoS locator; this is not a world-space flight-path vector. */
data class AirflowMarker(val horizontal: Double, val vertical: Double, val outsideScale: Boolean) {
    companion object {
        fun fromAngles(angleOfAttackDeg: Double?, sideslipDeg: Double?): AirflowMarker? {
            val aoa = angleOfAttackDeg?.takeIf { it.isFinite() } ?: return null
            val aos = sideslipDeg?.takeIf { it.isFinite() } ?: return null
            return AirflowMarker((-aos / 15).coerceIn(-1.0, 1.0), (aoa / 30).coerceIn(-1.0, 1.0),
                aos !in -15.0..15.0 || aoa !in -30.0..30.0)
        }
    }
}

/** Visible model limits in the same body-relative scale as the marker; no edge substitution. */
object AirflowLimits {
    fun fromTelemetry(telemetry: Telemetry, model: AircraftAlertModel?): List<Double> {
        val limits = model?.parametersFor(telemetry.aircraft)?.limits(telemetry.wingSweepRatio, telemetry.flapsPercent)
            ?: return emptyList()
        return listOfNotNull(
            limits.minAngleOfAttackDeg?.takeIf { it.isFinite() && it >= -30 && it < 0 },
            limits.maxAngleOfAttackDeg?.takeIf { it.isFinite() && it > 0 && it <= 30 })
    }
}

/** Margin to the FM positive AoA limit; not a prediction of stall or damage. */
data class PositiveAoaMargin(val degrees: Double, val fraction: Double) {
    companion object {
        fun fromTelemetry(telemetry: Telemetry, model: AircraftAlertModel?): PositiveAoaMargin? {
            val angle = telemetry.angleOfAttackDeg?.takeIf { it.isFinite() } ?: return null
            val limit = model?.parametersFor(telemetry.aircraft)
                ?.limits(telemetry.wingSweepRatio, telemetry.flapsPercent)?.maxAngleOfAttackDeg
                ?.takeIf { it.isFinite() && it > 0 } ?: return null
            val remaining = limit - angle
            val fraction = remaining / limit
            return if (remaining.isFinite() && fraction.isFinite()) PositiveAoaMargin(remaining, fraction) else null
        }
    }
}
