package voidmei.fm

import kotlin.math.*

/** Military baseline only. PistonModelBuilder adds the separate WEP stage adjustment. */
data class PistonMilitaryModel(val stages: List<CompressorStage>, val definitionRpm: Double)

object PistonMilitaryBuilder {
    /** Throws with a field diagnostic when required inputs cannot define a baseline. */
    fun build(raw: PistonEngineParameters, fuelPowerMultiplier: Double = 1.0): PistonMilitaryModel {
        require(fuelPowerMultiplier.isFinite() && fuelPowerMultiplier > 0) { "无效燃油功率倍率" }
        require(raw.stages.isNotEmpty()) { "缺少增压器级" }
        val military = requireNotNull(raw.militaryRpm?.takeIf { it.isFinite() && it > 0 }) { "缺少军用转速" }
        val definitionRpm = when {
            raw.shaftRpmMax != null && raw.wepRpm != null && raw.shaftRpmMax - military > 5 && raw.shaftRpmMax - raw.wepRpm < 5 -> raw.shaftRpmMax
            raw.rpmNominal != null && raw.rpmNominal - military > 5 -> raw.rpmNominal
            raw.governorMax != null && raw.governorMax - military > 5 -> raw.governorMax
            else -> military
        }
        require(definitionRpm.isFinite() && definitionRpm > 0) { "无效定义转速" }
        val adjust = definitionRpm - military > 5
        val rpmBoost = if (adjust) requireNotNull(PistonPowerCorrections.rpmPowerMultiplier(military, definitionRpm)) {
            "未定义的转速功率修正"
        } else 1.0
        if (adjust) require(raw.manifoldPressures.any { it.isFinite() && it > 0 }) { "缺少军用歧管压力，无法修正高度" }
        val rpmEffect = if (adjust) requireNotNull(PistonPowerCorrections.superchargerRpmMultiplier(
            military, definitionRpm, raw.pressureAtRpmZero ?: 0.3, raw.omegaFactorSquared ?: 1.0)) {
            "未定义的增压器转速修正"
        } else 1.0
        fun correctedAltitude(alt: Double) = (1 - ((1 - 0.0000225577 * alt).pow(5.25588) * rpmEffect)
            .pow(1 / 5.25588)) / 0.0000225577
        fun paired(a: Double?, b: Double?, label: String) {
            require((a == null) == (b == null)) { "$label 高度与功率必须同时存在" }
        }
        val deck = DoubleArray(raw.stages.size)
        val stages = raw.stages.mapIndexed { i, source ->
            paired(source.ceilingM, source.ceilingPowerHp, "升限")
            paired(source.constRpmAltitudeM, source.constRpmPowerHp, "恒转速")
            deck[i] = if (i == 0) (raw.deckPowerHp ?: (source.powerHp * 0.8)) * fuelPowerMultiplier
                else max(deck[i - 1] * 0.8, source.powerHp * 0.8 * fuelPowerMultiplier)
            CompressorStage(source.altitudeM, source.powerHp * fuelPowerMultiplier, deck[i],
                curvature = source.curvature ?: 1.0, speedManifoldMult = raw.speedManifoldMultiplier ?: 1.0,
                constRpmAlt = source.constRpmAltitudeM ?: 0.0,
                constRpmPower = (source.constRpmPowerHp ?: 0.0) * fuelPowerMultiplier,
                ceilingAlt = source.ceilingM ?: 0.0, ceilingPower = (source.ceilingPowerHp ?: 0.0) * fuelPowerMultiplier,
                exactAltitudes = raw.exactAltitudes ?: (raw.omegaFactorSquared == null))
        }.toMutableList()
        for (i in stages.indices) {
            val stage = stages[i]
            var updated = stage
            if (adjust && abs(rpmBoost - 1) >= 0.001) {
                val critAltitude = floor(correctedAltitude(stage.critAlt) + 0.5)
                val deckAltitude = correctedAltitude(0.0)
                val ratio = raw.deckPowerHp?.takeIf { it > 0 }?.div(raw.stages[0].powerHp) ?: 0.8
                val power = PistonPowerModel.interpolatePower(stage.oldPower, stage.oldAltitude,
                    stage.oldPower * ratio, stage.oldAltitude - raw.stages[0].altitudeM, critAltitude, 1.0) / rpmBoost
                updated = stage.copy(critAlt = critAltitude, critPower = power, deckAlt = deckAltitude,
                    constRpmPower = if (stage.constRpmPower == stage.oldPower) power else stage.constRpmPower / rpmBoost,
                    ceilingAlt = if (stage.ceilingAlt > 0) floor(correctedAltitude(stage.ceilingAlt) + 0.5) else stage.ceilingAlt,
                    deckPower = if (i == 0) PistonPowerModel.interpolatePower(stage.oldPower, stage.oldAltitude,
                        deck[0], 0.0, deckAltitude, 1.0) / rpmBoost else stage.deckPower)
                deck[i] = updated.deckPower
                for (j in i + 1 until stages.size) {
                    deck[j] = max(deck[j - 1] * 0.8, raw.stages[j].powerHp * 0.8 * fuelPowerMultiplier)
                    stages[j] = stages[j].copy(deckPower = deck[j])
                }
            }
            stages[i] = updated.copy(oldPowerNewRpm = stage.oldPower / rpmBoost,
                wepCritAlt = updated.critAlt, wepDeckAlt = updated.deckAlt)
        }
        return PistonMilitaryModel(stages.map { it.copy(stage0DeckAlt = stages[0].deckAlt) }, definitionRpm)
    }
}
