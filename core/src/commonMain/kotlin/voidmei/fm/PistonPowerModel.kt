package voidmei.fm

import kotlin.math.*

/** Adjusted FM stage parameters, in metres and horsepower; one engine, not aircraft total. */
data class CompressorStage(
    val critAlt: Double,
    val critPower: Double,
    val deckPower: Double,
    val deckAlt: Double = 0.0,
    val curvature: Double = 1.0,
    val wepCritAlt: Double = critAlt,
    val wepPowerMult: Double = 1.0,
    val speedManifoldMult: Double = 1.0,
    val constRpmAlt: Double = 0.0,
    val constRpmPower: Double = 0.0,
    val ceilingAlt: Double = 0.0,
    val ceilingPower: Double = 0.0,
    val oldAltitude: Double = critAlt,
    val oldPower: Double = critPower,
    val oldPowerNewRpm: Double = critPower,
    val wepDeckAlt: Double = 0.0,
    val wepConstRpmAlt: Double = 0.0,
    val stage0DeckAlt: Double = 0.0,
    val exactAltitudes: Boolean = false,
    val wepEnabled: Boolean = true,
) {
    init {
        require(listOf(critAlt, critPower, deckPower, deckAlt, curvature, wepCritAlt, wepPowerMult,
            speedManifoldMult, constRpmAlt, constRpmPower, ceilingAlt, ceilingPower, oldAltitude,
            oldPower, oldPowerNewRpm, wepDeckAlt, wepConstRpmAlt, stage0DeckAlt).all { it.isFinite() })
        require(critPower > 0) { "临界功率必须为正值" }
        require(deckPower >= 0) { "基准高度功率不能为负值" }
        require(curvature > 0) { "PowerConstRPMCurvature 必须为正值" }
        require(wepPowerMult > 0) { "WEP 功率倍率必须为正值" }
        require(speedManifoldMult >= 0) { "SpeedManifoldMultiplier 不能为负值" }
        require(constRpmPower >= 0) { "恒转速功率不能为负值" }
        require(ceilingPower >= 0) { "升限功率不能为负值" }
        require(oldPower >= 0 && oldPowerNewRpm >= 0) { "转速修正参考功率不能为负值" }
    }
}

data class PistonPowerPoint(val altitudeM: Double, val powerHp: Double, val stageIndex: Int)

/**
 * Migrates the legacy PistonPowerModel/PowerCurveHelper branch rules to common Kotlin.
 * Uses the original FM pressure and ram model, deliberately separate from StandardAtmosphere.
 * The density-based legacy “IAS” input is equivalent airspeed; it does not correct compressibility.
 * Agreement with the old implementation does not establish accuracy against the game.
 */
object PistonPowerModel {
    /** Legacy denominator: one-engine WEP peak sampled at 0–10 km and 0–800 km/h EAS. */
    fun peakWepPower(stages: List<CompressorStage>, checkActive: () -> Unit = {}): Double? {
        checkActive()
        if (stages.isEmpty()) return null
        var peak = 0.0
        for (altitude in 0..10000 step 100) {
            for (speed in 0..800 step 50) {
                val point = optimalPower(stages, altitude.toDouble(), wep = true,
                    speedKmh = speed.toDouble(), equivalentAirspeed = true, seaLevelTempC = 15.0,
                    checkActive = checkActive) ?: return null
                peak = maxOf(peak, point.powerHp)
            }
        }
        return peak.takeIf { it.isFinite() && it > 0 }
    }

    fun powerAtAltitude(
        stage: CompressorStage, altitudeM: Double, wep: Boolean = false,
        speedKmh: Double = 0.0, equivalentAirspeed: Boolean = false, seaLevelTempC: Double = 15.0,
    ): Double? {
        if (!altitudeM.isFinite() || altitudeM !in -4000.0..20000.0 ||
            !speedKmh.isFinite() || speedKmh < 0 || !seaLevelTempC.isFinite()) return null
        val temperatureK = 273.15 + seaLevelTempC - 0.0065 * altitudeM
        if (temperatureK <= 0) return null
        var effectiveAlt = altitudeM
        if (speedKmh > 0 && stage.speedManifoldMult > 0) {
            val p = pressure(altitudeM)
            val density = 101325.0 * p / (temperatureK * 287.0500676)
            val tas = if (equivalentAirspeed) speedKmh * sqrt(1.225 / density) else speedKmh
            val ramPressure = 0.5 * density * (tas / 3.6).pow(2) * stage.speedManifoldMult / 101325.0
            effectiveAlt = altitudeAtPressure(p + ramPressure)
        }
        val useWep = wep && stage.wepEnabled
        val (higherPower, higherAlt, lowerPower, lowerAlt, curvature) =
            bounds(stage, effectiveAlt, useWep, if (useWep) stage.wepPowerMult else 1.0)
        return interpolatePower(higherPower, higherAlt, lowerPower, lowerAlt, effectiveAlt, curvature)
            .takeIf { it.isFinite() && it >= 0 }
    }

    fun optimalPower(
        stages: List<CompressorStage>, altitudeM: Double, wep: Boolean = false,
        speedKmh: Double = 0.0, equivalentAirspeed: Boolean = false, seaLevelTempC: Double = 15.0,
        checkActive: () -> Unit = {},
    ): PistonPowerPoint? {
        checkActive()
        if (stages.isEmpty()) return null
        var best: PistonPowerPoint? = null
        for ((index, stage) in stages.withIndex()) {
            checkActive()
            // An undefined stage could be the best one; do not report a misleading optimum.
            val power = powerAtAltitude(stage, altitudeM, wep, speedKmh, equivalentAirspeed, seaLevelTempC) ?: return null
            if (best == null || power > best.powerHp) best = PistonPowerPoint(altitudeM, power, index)
        }
        return best
    }

    fun curve(stages: List<CompressorStage>, wep: Boolean = false, speedKmh: Double = 0.0,
        equivalentAirspeed: Boolean = false, seaLevelTempC: Double = 15.0, stepM: Int = 100, checkActive: () -> Unit = {},
    ): List<PistonPowerPoint?> {
        checkActive()
        require(stepM in 1..10000)
        return (0..10000 step stepM).map { optimalPower(stages, it.toDouble(), wep, speedKmh, equivalentAirspeed, seaLevelTempC, checkActive) }
    }

    private fun pressure(alt: Double) = (1 - 0.0000225577 * alt).pow(5.25588)
    private fun altitudeAtPressure(p: Double) = if (p <= 0) 20000.0 else (1 - p.pow(1 / 5.25588)) / 0.0000225577
    internal fun interpolatePower(higherPower: Double, higherAlt: Double, lowerPower: Double,
        lowerAlt: Double, targetAlt: Double, curvature: Double,
    ): Double {
        val denominator = pressure(higherAlt) - pressure(lowerAlt)
        if (abs(denominator) < 1e-9) return lowerPower
        val difference = if (targetAlt >= lowerAlt) higherPower - lowerPower else lowerPower - higherPower
        return lowerPower + difference * abs((pressure(targetAlt) - pressure(lowerAlt)) / denominator).pow(curvature)
    }
    private fun bounds(p: CompressorStage, altRam: Double, isWep: Boolean, wepMult: Double): DoubleArray {
        var higherPower: Double
        var higherAlt: Double
        var lowerPower: Double
        var lowerAlt: Double
        var curvature = 1.0
        if (!isWep) {
            if (altRam <= p.critAlt) {
                if (hasConstRpm(p) && constRpmBelowDeck(p) && altRam < p.constRpmAlt) {
                    higherAlt = p.constRpmAlt
                    higherPower = 0.0
                    lowerAlt = p.constRpmAlt - 10.0
                    lowerPower = 0.0
                } else if (!constRpmBelowCritAlt(p) && !powerIsDeckPower(p)) {
                    higherAlt = p.critAlt
                    higherPower = p.critPower
                    lowerAlt = p.deckAlt
                    lowerPower = p.deckPower
                } else if (constRpmBelowCritAlt(p) && altRam < p.constRpmAlt) {
                    higherAlt = p.constRpmAlt
                    higherPower = p.constRpmPower
                    lowerAlt = p.deckAlt
                    lowerPower = p.deckPower
                } else if (constRpmBelowCritAlt(p) && altRam >= p.constRpmAlt) {
                    curvature = p.curvature
                    higherAlt = p.critAlt
                    higherPower = p.critPower
                    lowerAlt = p.constRpmAlt
                    lowerPower = p.constRpmPower
                } else {
                    higherAlt = p.ceilingAlt
                    higherPower = p.ceilingPower
                    lowerAlt = p.critAlt
                    lowerPower = p.critPower
                }
            } else if (altRam <= p.oldAltitude) {
                lowerAlt = p.critAlt
                lowerPower = p.critPower
                if (!ceilingIsUseful(p)) {
                    higherAlt = p.oldAltitude
                    higherPower = interpolatePower(p.oldPowerNewRpm, p.critAlt,
                            p.deckPower, p.deckAlt, p.critAlt, curvature) *
                            (pressure(p.oldAltitude) / pressure(p.critAlt))
                } else if (!constRpmAboveCritAlt(p)) {
                    if (p.exactAltitudes) {
                        higherAlt = p.oldAltitude
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(p.critAlt) / pressure(p.critAlt)))
                        higherPower = interpolatePower(p.ceilingPower, ceilScaledAlt,
                                p.oldPowerNewRpm, p.critAlt, p.oldAltitude, curvature)
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                } else {
                    curvature = p.curvature
                    if (p.exactAltitudes) {
                        higherAlt = p.oldAltitude
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(p.critAlt) / pressure(p.critAlt)))
                        higherPower = interpolatePower(p.ceilingPower, ceilScaledAlt,
                                p.oldPowerNewRpm, p.critAlt, p.oldAltitude, curvature)
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                }
            } else {
                if (!ceilingIsUseful(p)) {
                    lowerAlt = p.oldAltitude
                    lowerPower = interpolatePower(p.oldPowerNewRpm, p.critAlt,
                            p.deckPower, p.deckAlt, p.critAlt, curvature) *
                            (pressure(p.oldAltitude) / pressure(p.critAlt))
                    higherAlt = altRam
                    higherPower = lowerPower * (pressure(altRam) / pressure(lowerAlt))
                } else if (!constRpmAboveCritAlt(p)) {
                    if (p.exactAltitudes) {
                        lowerAlt = p.oldAltitude
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(p.critAlt) / pressure(p.critAlt)))
                        lowerPower = interpolatePower(p.ceilingPower, ceilScaledAlt,
                                p.oldPowerNewRpm, p.critAlt, p.oldAltitude, curvature)
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    } else {
                        lowerAlt = p.critAlt
                        lowerPower = p.critPower
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                } else {
                    curvature = p.curvature
                    if (p.exactAltitudes) {
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(p.critAlt) / pressure(p.critAlt)))
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                        lowerAlt = p.oldAltitude
                        lowerPower = interpolatePower(p.ceilingPower, ceilScaledAlt,
                                p.oldPowerNewRpm, p.critAlt, p.oldAltitude, curvature)
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                        lowerAlt = p.oldAltitude
                        lowerPower = p.critPower
                    }
                }
            }
        } else {
            val wepCritAlt = p.wepCritAlt
            if (altRam <= wepCritAlt && altRam <= p.oldAltitude) {
                if (hasConstRpm(p) && constRpmBelowDeck(p) && altRam < p.constRpmAlt) {
                    higherAlt = p.constRpmAlt
                    higherPower = 0.0
                    lowerAlt = p.constRpmAlt - 10.0
                    lowerPower = 0.0
                } else if (!constRpmBelowCritAlt(p) && !powerIsDeckPower(p)) {
                    if (p.exactAltitudes) {
                        higherAlt = wepCritAlt
                        higherPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.deckPower * wepMult, p.deckAlt,
                                higherAlt, curvature)
                        lowerAlt = p.wepDeckAlt
                        lowerPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.deckPower * wepMult, p.deckAlt,
                                lowerAlt, curvature)
                    } else {
                        higherAlt = wepCritAlt
                        higherPower = p.critPower * wepMult
                        lowerAlt = p.stage0DeckAlt
                        lowerPower = p.deckPower * wepMult
                    }
                } else if (p.exactAltitudes && hasConstRpm(p) && altRam < p.constRpmAlt) {
                    higherPower = p.constRpmPower * wepMult
                    lowerAlt = p.deckAlt
                    lowerPower = p.deckPower * wepMult
                    higherAlt = p.constRpmAlt
                } else if (!p.exactAltitudes && hasConstRpm(p) && altRam < p.wepConstRpmAlt) {
                    higherPower = p.constRpmPower * wepMult
                    lowerAlt = p.deckAlt
                    lowerPower = p.deckPower * wepMult
                    higherAlt = p.wepConstRpmAlt
                } else if (p.exactAltitudes && hasConstRpm(p) && altRam >= p.constRpmAlt) {
                    curvature = p.curvature
                    higherAlt = wepCritAlt
                    lowerPower = p.constRpmPower * wepMult
                    lowerAlt = p.constRpmAlt
                    higherPower = interpolatePower(
                            p.critPower * wepMult, p.critAlt,
                            p.constRpmPower * wepMult, p.constRpmAlt,
                            higherAlt, curvature)
                } else if (!p.exactAltitudes && hasConstRpm(p) && altRam >= p.wepConstRpmAlt) {
                    curvature = p.curvature
                    higherAlt = wepCritAlt
                    lowerPower = p.constRpmPower * wepMult
                    lowerAlt = p.wepConstRpmAlt
                    higherPower = p.critPower * wepMult
                } else if (powerIsDeckPower(p)) {
                    if (p.exactAltitudes) {
                        higherAlt = p.ceilingAlt
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                        higherPower = interpolatePower(
                                p.ceilingPower * wepMult, ceilScaledAlt,
                                p.critPower * wepMult, wepCritAlt,
                                p.ceilingAlt, curvature)
                        lowerAlt = wepCritAlt
                        lowerPower = p.critPower * wepMult
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                        lowerAlt = wepCritAlt
                        lowerPower = p.critPower * wepMult
                    }
                } else {
                    higherAlt = wepCritAlt
                    higherPower = p.critPower * wepMult
                    lowerAlt = p.deckAlt
                    lowerPower = p.deckPower * wepMult
                }
            } else if (p.oldAltitude < altRam && altRam <= wepCritAlt) {
                higherAlt = wepCritAlt
                higherPower = interpolatePower(
                        p.critPower * wepMult, p.critAlt,
                        p.deckPower * wepMult, p.deckAlt,
                        p.oldAltitude, curvature)
                lowerAlt = p.oldAltitude
                lowerPower = higherPower
            } else if (floor(wepCritAlt + 0.5) < altRam && altRam <= floor(p.oldAltitude + 0.5)) {
                if (!constRpmBelowWepCritAlt(p)) {
                    lowerAlt = wepCritAlt
                    if (p.exactAltitudes) {
                        lowerPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.deckPower * wepMult, p.deckAlt,
                                lowerAlt, curvature)
                    } else {
                        lowerPower = p.critPower * wepMult
                    }
                } else {
                    lowerAlt = wepCritAlt
                    if (p.exactAltitudes) {
                        lowerPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.constRpmPower * wepMult, p.constRpmAlt,
                                lowerAlt, p.curvature)
                    } else {
                        lowerPower = p.critPower * wepMult
                    }
                }
                if (!ceilingIsUseful(p)) {
                    higherAlt = p.oldAltitude
                    higherPower = interpolatePower(
                            p.critPower * wepMult, p.critAlt,
                            p.deckPower * wepMult, p.deckAlt,
                            higherAlt, curvature) *
                            (pressure(p.oldAltitude) / pressure(lowerAlt))
                } else if (!constRpmAboveCritAlt(p)) {
                    if (p.exactAltitudes) {
                        higherAlt = p.oldAltitude
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                        higherPower = interpolatePower(
                                p.ceilingPower * wepMult, ceilScaledAlt,
                                p.oldPowerNewRpm * wepMult, wepCritAlt,
                                p.oldAltitude, curvature)
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                } else {
                    curvature = p.curvature
                    if (p.exactAltitudes) {
                        higherAlt = p.oldAltitude
                        val ceilScaledAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                        higherPower = interpolatePower(
                                p.ceilingPower * wepMult, ceilScaledAlt,
                                p.oldPowerNewRpm * wepMult, wepCritAlt,
                                p.oldAltitude, curvature)
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                }
            } else {
                if (wepCritAlt < p.critAlt) {
                    lowerAlt = p.oldAltitude
                    if (!ceilingIsUseful(p)) {
                        lowerPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.deckPower * wepMult, p.deckAlt,
                                lowerAlt, curvature) *
                                (pressure(p.oldAltitude) / pressure(wepCritAlt))
                    } else {
                        if (p.exactAltitudes) {
                            val ceilScaledAlt = altitudeAtPressure(
                                    pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                            lowerPower = interpolatePower(
                                    p.ceilingPower * wepMult, ceilScaledAlt,
                                    p.oldPowerNewRpm * wepMult, wepCritAlt,
                                    lowerAlt, curvature)
                        } else {
                            lowerAlt = wepCritAlt
                            lowerPower = p.critPower * wepMult
                        }
                    }
                } else if (!constRpmBelowCritAlt(p)) {
                    lowerAlt = wepCritAlt
                    if (p.exactAltitudes) {
                        lowerPower = interpolatePower(
                                p.critPower * wepMult, p.critAlt,
                                p.deckPower * wepMult, p.deckAlt,
                                p.oldAltitude, curvature)
                    } else {
                        lowerPower = p.critPower * wepMult
                    }
                } else {
                    lowerAlt = wepCritAlt
                    lowerPower = interpolatePower(
                            p.critPower * wepMult, p.critAlt,
                            p.constRpmPower * wepMult, p.constRpmAlt,
                            lowerAlt, curvature)
                }
                if (!ceilingIsUseful(p)) {
                    higherAlt = altRam
                    higherPower = lowerPower * (pressure(altRam) / pressure(lowerAlt))
                } else if (!constRpmAboveCritAlt(p)) {
                    if (p.exactAltitudes) {
                        higherAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                        higherPower = p.ceilingPower * wepMult
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                } else {
                    curvature = p.curvature
                    if (p.exactAltitudes) {
                        higherAlt = altitudeAtPressure(
                                pressure(p.ceilingAlt) * (pressure(wepCritAlt) / pressure(p.critAlt)))
                        higherPower = p.ceilingPower
                    } else {
                        higherAlt = p.ceilingAlt
                        higherPower = p.ceilingPower
                    }
                }
                if (higherAlt < lowerAlt && higherPower > lowerPower) {
                    val tmpAlt = lowerAlt
                    val tmpPwr = lowerPower
                    lowerAlt = higherAlt
                    lowerPower = higherPower
                    higherAlt = tmpAlt
                    higherPower = tmpPwr
                }
            }
        }
        return doubleArrayOf(higherPower, higherAlt, lowerPower, lowerAlt, curvature)
    }
    private fun hasConstRpm(p: CompressorStage): Boolean {
        return p.constRpmPower > 0
    }
    private fun constRpmBelowCritAlt(p: CompressorStage): Boolean {
        return hasConstRpm(p) && (p.constRpmAlt - p.critAlt) < -1
    }
    private fun constRpmBelowOldCritAlt(p: CompressorStage): Boolean {
        return hasConstRpm(p) && (p.constRpmAlt - p.oldAltitude) < -1
    }
    private fun constRpmBelowWepCritAlt(p: CompressorStage): Boolean {
        return hasConstRpm(p) && (p.constRpmAlt - p.wepCritAlt) < -1
    }
    private fun constRpmAboveCritAlt(p: CompressorStage): Boolean {
        return hasConstRpm(p)
            && p.constRpmAlt == p.critAlt
            && p.critPower - p.ceilingPower > 1
            && p.curvature > 1
    }
    private fun constRpmBelowDeck(p: CompressorStage): Boolean {
        return hasConstRpm(p) && p.constRpmAlt <= 0
    }
    private fun hasCeiling(p: CompressorStage): Boolean {
        return p.ceilingAlt > 0 && p.ceilingPower > 0
    }
    private fun ceilingIsUseful(p: CompressorStage): Boolean {
        val referenceAlt = if (p.oldAltitude > 0) p.oldAltitude else p.critAlt
        val referencePower = if (p.oldPower > 0) p.oldPower else p.critPower
        return hasCeiling(p)
            && (p.ceilingAlt - referenceAlt) >= 2
            && (referencePower - p.ceilingPower) >= 2
    }
    private fun powerIsDeckPower(p: CompressorStage): Boolean {
        return abs(p.critAlt - p.deckAlt) < 1
    }
}
