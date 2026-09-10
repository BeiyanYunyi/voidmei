package voidmei.telemetry

/** Radar metres assume that the radio altimeter shares the inferred cockpit altitude unit. */
enum class HudAltitudeMode(val id: String, val label: String) {
    SEA_LEVEL("sea_level", "海拔高度"),
    LOW_RADAR("low_radar", "低空优先雷达"),
    ALWAYS_RADAR("always_radar", "始终优先雷达");

    fun reading(flight: ConnectionState.Flying): HudAltitudeReading {
        val radar = HudField.RADIO_ALTITUDE_ESTIMATE.value(flight)
        if (radar != null && (this == ALWAYS_RADAR || (this == LOW_RADAR && radar <= 500)))
            return HudAltitudeReading(radar, true)
        return HudAltitudeReading(flight.telemetry.altitudeM?.takeIf { it.isFinite() }, false)
    }
}

data class HudAltitudeReading(val metres: Double?, val radarEstimated: Boolean) {
    val unit: String get() = if (radarEstimated) "m · 雷达估计" else "m"
}
