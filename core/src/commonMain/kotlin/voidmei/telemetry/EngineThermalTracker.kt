package voidmei.telemetry

import voidmei.fm.EngineThermalBand
import voidmei.fm.EngineThermalParameters

/** Bounds within the FM budget model, not engine lifetime or measured damage. */
data class ThermalBudgetRange(val minimumSeconds: Double, val maximumSeconds: Double)
data class ThermalBandBudget(val index: Int, val active: Boolean, val remaining: ThermalBudgetRange)
data class ThermalChannelBudget(val bands: List<ThermalBandBudget>) {
    val activeRemaining: ThermalBudgetRange? get() {
        val active = bands.filter { it.active }
        return if (active.isEmpty()) null else ThermalBudgetRange(
            active.minOf { it.remaining.minimumSeconds }, active.minOf { it.remaining.maximumSeconds })
    }
}
data class EngineThermalBudget(val telemetryIndex: Int, val water: ThermalChannelBudget?, val oil: ThermalChannelBudget?)

/**
 * Integrates the previous sample's temperature over each observed interval (sample-and-hold).
 * Initial wear is unknown: every positive WorkTime starts at [0, WorkTime]. Missing samples,
 * long gaps, backward clocks and model/aircraft changes discard unsupported history.
 * Missing/zero WorkTime does not create a budget; missing/zero RecoverTime does not restore it.
 * Exhaustion saturates at zero. No damage debt or inferred engine-off instant refill is modelled.
 */
class EngineThermalTracker(private val maximumGapMs: Long = 2_500) {
    init { require(maximumGapMs > 0) }
    private var aircraft: String? = null
    private var models: List<EngineThermalParameters> = emptyList()
    private var previousTime: Long? = null
    private val channels = mutableMapOf<Pair<Int, Boolean>, Channel>()

    fun reset() {
        aircraft = null
        models = emptyList()
        previousTime = null
        channels.clear()
    }

    fun update(aircraft: String, models: List<EngineThermalParameters>, engines: List<Engine>, nowMs: Long): List<EngineThermalBudget> {
        require(aircraft.isNotBlank())
        if (!aircraft.equals(this.aircraft, ignoreCase = true) || this.models != models) {
            reset()
            this.aircraft = aircraft
            this.models = models.map { it.copy(bands = it.bands.toList()) }
        }
        val elapsed = previousTime?.let { previous ->
            (nowMs - previous).takeIf { nowMs >= previous && it in 0..maximumGapMs }
        }
        if (elapsed == null) channels.clear()
        previousTime = nowMs
        val seconds = (elapsed ?: 0) / 1000.0
        val actual = engines.groupBy { it.index }
        return models.groupBy { it.telemetryIndex }.mapNotNull { (index, candidates) ->
            val model = candidates.singleOrNull()?.takeIf { index > 0 && valid(it) } ?: return@mapNotNull null
            val engine = actual[index]?.singleOrNull()
            fun channel(water: Boolean): ThermalChannelBudget? {
                val key = index to water
                val temperature = (if (water) engine?.waterTemperatureC else engine?.oilTemperatureC)
                    ?.takeIf { it.isFinite() && it >= -273.15 }
                if (temperature == null) {
                    channels.remove(key)
                    return null
                }
                val bands = model.bands.filter { threshold(it, water) != null && (it.workSeconds ?: 0.0) > 0.0 }
                if (bands.isEmpty()) return null
                val channelSeconds = if (key in channels) seconds else 0.0
                val state = channels.getOrPut(key) { Channel(bands.map { ThermalBudgetRange(0.0, it.workSeconds!!) }, temperature) }
                state.remaining = bands.zip(state.remaining).map { (band, range) ->
                    val work = band.workSeconds!!
                    val delta = if (state.temperature >= threshold(band, water)!!) -channelSeconds
                        else if ((band.recoverSeconds ?: 0.0) > 0.0 && channelSeconds > 0.0) channelSeconds / band.recoverSeconds!! * work else 0.0
                    ThermalBudgetRange((range.minimumSeconds + delta).coerceIn(0.0, work),
                        (range.maximumSeconds + delta).coerceIn(0.0, work))
                }
                state.temperature = temperature
                return ThermalChannelBudget(bands.zip(state.remaining).map { (band, range) ->
                    ThermalBandBudget(band.index, temperature >= threshold(band, water)!!, range)
                })
            }
            EngineThermalBudget(index, channel(true), channel(false))
        }
    }

    private class Channel(var remaining: List<ThermalBudgetRange>, var temperature: Double)
    private fun threshold(band: EngineThermalBand, water: Boolean) = if (water) band.waterTemperatureC else band.oilTemperatureC
    private fun valid(model: EngineThermalParameters): Boolean = model.bands.size <= 64 &&
        model.bands.map { it.index }.distinct().size == model.bands.size && model.bands.all { band ->
            band.index >= 0 && listOfNotNull(band.workSeconds, band.recoverSeconds).all { it.isFinite() && it >= 0 } &&
                listOfNotNull(band.waterTemperatureC, band.oilTemperatureC).all { it.isFinite() && it >= -273.15 }
        }
}
