package voidmei.telemetry

import voidmei.fm.PistonModels
import voidmei.fm.PistonPowerModel

data class CompressorRecommendation(val engineIndex: Int, val actualStage: Int, val recommendedStage: Int)

/** Uses the supplied fuel-adjusted models at 15 C and current TAS. API stages are one-based. */
object CompressorAdvice {
    fun recommendations(telemetry: Telemetry, models: Map<Int, PistonModels>): List<CompressorRecommendation> {
        val altitude = telemetry.altitudeM ?: return emptyList()
        val speed = telemetry.tasKmh ?: return emptyList()
        return telemetry.engines.groupBy { it.index }.mapNotNull { (index, engines) ->
            val engine = engines.singleOrNull()?.takeIf { index > 0 } ?: return@mapNotNull null
            val throttle = engine.throttlePercent?.takeIf { it.isFinite() && it >= 100 } ?: return@mapNotNull null
            val model = models[index] ?: return@mapNotNull null
            val wep = throttle > 100
            val stages = (if (wep) model.wepStages else model.military.stages)?.takeIf { it.size > 1 } ?: return@mapNotNull null
            val actual = engine.compressorStage?.takeIf { it.isFinite() && it in 1.0..stages.size.toDouble() && it % 1.0 == 0.0 }
                ?.toInt() ?: return@mapNotNull null
            val best = PistonPowerModel.optimalPower(stages, altitude, wep, speed) ?: return@mapNotNull null
            val currentPower = PistonPowerModel.powerAtAltitude(stages[actual - 1], altitude, wep, speed) ?: return@mapNotNull null
            // Equal-power stages need no shift, even when the optimiser picks an earlier index.
            if (best.powerHp <= currentPower) return@mapNotNull null
            CompressorRecommendation(index, actual, best.stageIndex + 1)
        }
    }
}
