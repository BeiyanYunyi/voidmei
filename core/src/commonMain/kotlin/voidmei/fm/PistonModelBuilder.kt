package voidmei.fm

import kotlin.math.abs
import kotlin.math.floor

data class PistonModels(
    val military: PistonMilitaryModel,
    val wepStages: List<CompressorStage>?,
    val wepIssue: String?,
)

object PistonModelBuilder {
    fun build(raw: PistonEngineParameters, fuelPowerMultiplier: Double = 1.0, fuel: FuelModification? = null): PistonModels {
        val soviet = fuel?.inverted == false && fuel.id in setOf("ussr_fuel_b-95", "ussr_fuel_b-100") &&
            fuel?.addedHorsepower?.let { abs(it - 50.0) <= 0.01 } == true
        val military = PistonMilitaryBuilder.build(raw, fuelPowerMultiplier * if (soviet) 1.018 else 1.0)
        return try { PistonModels(military, britishFuel(raw, wep(raw, military), fuel), null) }
        catch (e: IllegalArgumentException) { PistonModels(military, null, e.message ?: "WEP 参数不可用") }
    }

    private fun britishFuel(raw: PistonEngineParameters, stages: List<CompressorStage>, fuel: FuelModification?): List<CompressorStage> {
        if (fuel == null || fuel.inverted || fuel.id !in setOf("150_octan_fuel", "100_octan_spitfire")) return stages
        return stages.mapIndexed { index, stage ->
            if (!stage.wepEnabled) stage else {
                val militaryRpm = requireNotNull(raw.militaryRpm) { "缺少军用转速" }
                val wepRpm = requireNotNull(raw.wepRpm) { "缺少 WEP 转速" }
                val multiplier = requireNotNull(PistonPowerCorrections.wepPowerMultiplier(raw.afterburnerBoost ?: 1.0,
                    raw.throttleBoost ?: 1.0, raw.stages[index].afterburnerBoost ?: 1.0,
                    fuel.afterburnerMultiplier ?: 1.0, militaryRpm, wepRpm)) { "无效燃油 WEP 倍率" }
                var critical = stage.wepCritAlt
                if (abs(multiplier - 1) > 0.001) {
                    val militaryMP = requireNotNull(raw.manifoldPressures.filter { it > 0 && it.isFinite() }.maxOrNull()) { "缺少军用歧管压力" }
                    val wepMP = requireNotNull(raw.wepManifoldPressure) { "缺少 WEP 歧管压力" }
                    val modifiedMP = militaryMP + (wepMP - militaryMP) * (fuel.compressorMultiplier ?: 1.0)
                    val rpmEffect = requireNotNull(PistonPowerCorrections.superchargerRpmMultiplier(militaryRpm, wepRpm,
                        raw.pressureAtRpmZero ?: 0.3, raw.omegaFactorSquared ?: 1.0)) { "无效燃油增压器修正" }
                    critical = floor(requireNotNull(PistonPowerCorrections.wepCriticalAltitude(stage.critAlt, militaryMP,
                        modifiedMP, rpmEffect, raw.stages[index].afterburnerPressureBoost ?: 1.0)) { "无法计算燃油 WEP 高度" } + 0.5)
                }
                // Match the legacy post-processing order: fuel changes critical altitude,
                // while deck and ConstRPM altitude retain their pre-fuel values.
                stage.copy(wepPowerMult = multiplier, wepCritAlt = critical)
            }
        }
    }

    private fun wep(raw: PistonEngineParameters, military: PistonMilitaryModel): List<CompressorStage> =
        military.stages.mapIndexed { index, stage ->
            val source = raw.stages[index]
            if (source.afterburnerBoost == 0.0) {
                // Disabling a stage must also disable WEP-specific curve branches.
                stage.copy(wepEnabled = false)
            } else {
                val militaryRpm = requireNotNull(raw.militaryRpm) { "缺少军用转速" }
                val wepRpm = requireNotNull(raw.wepRpm) { "缺少 WEP 转速" }
                val multiplier = requireNotNull(PistonPowerCorrections.wepPowerMultiplier(
                    raw.afterburnerBoost ?: 1.0, raw.throttleBoost ?: 1.0, source.afterburnerBoost ?: 1.0,
                    raw.octaneMultiplier ?: 1.0, militaryRpm, wepRpm)) { "无效 WEP 功率倍率" }
                val shift = abs(multiplier - 1.0) >= 0.001
                val shiftConstRpm = !stage.exactAltitudes && stage.constRpmAlt != 0.0 && stage.constRpmPower > 0
                fun altitude(value: Double): Double {
                    val manifold = requireNotNull(raw.manifoldPressures.filter { it.isFinite() && it > 0 }.maxOrNull()) {
                        "缺少军用歧管压力"
                    }
                    val wepManifold = requireNotNull(raw.wepManifoldPressure) { "缺少 WEP 歧管压力" }
                    val rpm = requireNotNull(PistonPowerCorrections.superchargerRpmMultiplier(militaryRpm, wepRpm,
                        raw.pressureAtRpmZero ?: 0.3, raw.omegaFactorSquared ?: 1.0)) { "无效 WEP 增压器修正" }
                    return requireNotNull(PistonPowerCorrections.wepCriticalAltitude(value, manifold, wepManifold,
                        rpm, source.afterburnerPressureBoost ?: 1.0)) { "无法计算 WEP 高度" }
                }
                stage.copy(wepPowerMult = multiplier,
                    wepCritAlt = if (shift) floor(altitude(stage.critAlt) + 0.5) else stage.critAlt,
                    wepDeckAlt = if (shift) floor(altitude(stage.deckAlt) + 0.5) else stage.deckAlt,
                    wepConstRpmAlt = if (shiftConstRpm) altitude(stage.constRpmAlt) else stage.wepConstRpmAlt)
            }
        }
}
