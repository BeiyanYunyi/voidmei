package voidmei.telemetry

/** Scale shared by the speed-limit readout and its optional model markers. */
data class SpeedLimitScale(
    val ratio: Double,
    val limitingMach: Boolean,
    val stallRatio: Double?,
    val aileronRatio: Double?,
    val rudderRatio: Double?,
    val machOneRatio: Double?,
) {
    companion object {
        fun fromFlight(flight: ConnectionState.Flying, model: AircraftAlertModel?): SpeedLimitScale? {
            val t = flight.telemetry
            val parameters = model?.parametersFor(t.aircraft) ?: return null
            val limits = parameters.limits(t.wingSweepRatio, t.flapsPercent) ?: return null
            fun Double?.positive() = this?.takeIf { it.isFinite() && it > 0 }
            val vne = limits.vneKmh.positive() ?: return null
            val mne = limits.maxMach.positive() ?: return null
            val ias = t.iasKmh?.takeIf { it.isFinite() && it >= 0 } ?: return null
            val mach = t.mach?.takeIf { it.isFinite() && it >= 0 } ?: return null
            val iasRatio = ias / vne
            val machRatio = mach / mne
            val ratio = maxOf(iasRatio, machRatio).takeIf { it.isFinite() } ?: return null
            val limitingMach = machRatio > iasRatio
            val iasPerMach = if (ias > 0 && mach > 0) (ias / mach).positive() else null
            val scaleKmh = if (limitingMach) iasPerMach?.let { (it * mne).positive() } else vne
            fun marker(speed: Double?): Double? = speed.positive()?.let { value ->
                scaleKmh?.let { (value / it).positive() }
            }
            return SpeedLimitScale(ratio, limitingMach,
                marker(HudField.STALL_IAS.value(flight, model)),
                marker(parameters.controlSpeeds.aileronKmh), marker(parameters.controlSpeeds.rudderKmh),
                if (limitingMach) (1 / mne).positive() else marker(iasPerMach))
        }
    }
}
