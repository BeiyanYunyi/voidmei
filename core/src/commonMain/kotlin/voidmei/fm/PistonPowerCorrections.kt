package voidmei.fm

import kotlin.math.pow

/** Legacy FM RPM/boost equations. Unknown or undefined inputs stay unknown. */
object PistonPowerCorrections {
    fun rpmPowerMultiplier(fromRpm: Double, toRpm: Double): Double? {
        if (!positive(fromRpm, toRpm)) return null
        // Normalize before evaluating the cubic to avoid overflowing with large finite RPMs.
        val ratio = fromRpm / toRpm
        val denominator = ratio * ratio * (1.5 - ratio)
        return (0.5 / denominator).takeIf { denominator > 0 && it.isFinite() && it > 0 }
    }

    fun superchargerRpmMultiplier(militaryRpm: Double, wepRpm: Double,
        pressureAtRpmZero: Double, omegaFactorSquared: Double,
    ): Double? {
        if (!positive(militaryRpm, wepRpm) || !pressureAtRpmZero.isFinite() ||
            !omegaFactorSquared.isFinite()) return null
        val base = 1 + (1 - pressureAtRpmZero) / militaryRpm * (wepRpm - militaryRpm)
        if (base <= 0) return null
        return base.pow(1 + omegaFactorSquared).takeIf { it.isFinite() && it > 0 }
    }

    fun wepPowerMultiplier(afterburnerBoost: Double, throttleBoost: Double,
        stageBoost: Double, octaneMultiplier: Double, militaryRpm: Double, wepRpm: Double,
    ): Double? {
        if (!positive(afterburnerBoost, throttleBoost, stageBoost) ||
            !octaneMultiplier.isFinite() || octaneMultiplier < 0) return null
        val rpm = rpmPowerMultiplier(militaryRpm, wepRpm) ?: return null
        val fuelBoost = 1 + (afterburnerBoost - 1) * octaneMultiplier
        return (fuelBoost * throttleBoost * stageBoost * rpm).takeIf { it.isFinite() && it > 0 }
    }

    /** Both manifold pressures must use the same unit (legacy FM uses ata). */
    fun wepCriticalAltitude(militaryAltitudeM: Double, militaryManifoldPressure: Double,
        wepManifoldPressure: Double, rpmMultiplier: Double, pressureBoost: Double,
    ): Double? {
        if (!militaryAltitudeM.isFinite() || militaryAltitudeM !in -4000.0..20000.0 ||
            !positive(militaryManifoldPressure, wepManifoldPressure, rpmMultiplier, pressureBoost)) return null
        val militaryPressure = (1 - 0.0000225577 * militaryAltitudeM).pow(5.25588)
        val wepPressure = wepManifoldPressure / (militaryManifoldPressure / militaryPressure * rpmMultiplier * pressureBoost)
        if (!wepPressure.isFinite() || wepPressure <= 0) return null
        return ((1 - wepPressure.pow(1 / 5.25588)) / 0.0000225577).takeIf { it.isFinite() }
    }

    private fun positive(vararg numbers: Double) = numbers.all { it.isFinite() && it > 0 }
}
