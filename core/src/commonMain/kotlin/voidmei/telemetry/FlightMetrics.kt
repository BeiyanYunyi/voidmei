package voidmei.telemetry

import kotlin.math.*
import voidmei.physics.StandardAtmosphere

data class FlightMetrics(
    val energyHeightM: Double? = null,
    val accelerationMps2: Double? = null,
    val specificExcessPowerMps: Double? = null,
    val estimatedTurnRadiusM: Double? = null,
    val estimatedTurnRateDegps: Double? = null,
    val fuelPercent: Double? = null,
    val fuelConsumptionKgPerMinute: Double? = null,
    val fuelEnduranceSeconds: Double? = null,
    val totalPowerHp: Double? = null,
    val totalThrustKgf: Double? = null,
    val thrustPowerKw: Double? = null,
    val standardDensityKgM3: Double? = null,
    val standardDynamicPressurePa: Double? = null,
    val wepFuel: WepFuelObservation? = null,
    val engineResponsePercentPerSecond: Double? = null,
    val observedEnginePeak: ObservedEnginePeak? = null,
    val cockpitAltitudeUnit: CockpitAltitudeUnit? = null,
)

/** One instance per connection; only monotonic timestamps are accepted. */
class FlightCalculator {
    private var previous: Pair<Telemetry, Long>? = null
    private val fuelEstimator = FuelEstimator()
    private val observedPeak = ObservedEnginePeakTracker()
    private val cockpitUnits = CockpitUnitEstimator()
    private val speedTrend = SpeedTrend()

    private fun resetSampling() { previous = null; fuelEstimator.reset(); cockpitUnits.reset(); speedTrend.reset() }
    fun reset() { resetSampling(); observedPeak.reset() }

    /** Drop interval-dependent calculations while preserving a validated engine reference. */
    fun pause() { resetSampling(); observedPeak.pause() }

    fun update(current: Telemetry, timeMs: Long): FlightMetrics {
        val prior = previous
        previous = current to timeMs
        val speed = current.tasKmh?.takeIf { it.isFinite() && it >= 0 }?.div(3.6)
        val energy = if (speed != null && current.altitudeM != null) current.altitudeM + speed * speed / (2 * G) else null
        val dt = prior?.takeIf { timeMs > it.second && timeMs - it.second in 1..MAXIMUM_SAMPLE_GAP_MS }
            ?.let { (timeMs - it.second) / 1000.0 }
        val sameFlight = prior != null && prior.first.aircraft == current.aircraft && dt != null && dt > 0
        if (!sameFlight || prior?.first?.fuelCapacityKg != current.fuelCapacityKg) fuelEstimator.reset()
        if (!sameFlight || speed == null) speedTrend.reset()
        val trend = speed?.let { speedTrend.update(it, timeMs) }
        val acceleration = trend?.first
        val sep = if (trend != null && current.verticalSpeedMps != null)
            current.verticalSpeedMps + trend.second else null
        val turnAcceleration = (if (current.loadG != null && current.rollDeg != null && current.pitchDeg != null && current.angleOfAttackDeg != null) {
            val roll = current.rollDeg * PI / 180
            // Raw instrument pitch is nose-down positive, so this is the negative flight-path angle.
            // Do not substitute the nose-up-positive attitude display angle here.
            val pitch = (current.pitchDeg + current.angleOfAttackDeg) * PI / 180
            G * sqrt((current.loadG * current.loadG + 1 - 2 * current.loadG * cos(roll) * cos(pitch)).coerceAtLeast(0.0))
        } else null).finite()
        val radius = if (speed != null && speed > 1 && turnAcceleration != null && turnAcceleration > 1e-6)
            speed * speed / turnAcceleration else null
        val turnRate = if (speed != null && speed > 1 && turnAcceleration != null) turnAcceleration / speed * 180 / PI else null
        val fuel = fuelEstimator.update(current.fuelKg, timeMs)
        val fuelPercent = if (current.fuelKg != null && current.fuelKg.isFinite() && current.fuelKg >= 0 && current.fuelCapacityKg != null && current.fuelCapacityKg.isFinite() && current.fuelCapacityKg > 0)
            (current.fuelKg / current.fuelCapacityKg * 100).coerceIn(0.0, 100.0) else null
        // A partial engine list must not silently understate total power/thrust.
        fun total(value: (Engine) -> Double?): Double? = current.engines.takeIf { it.isNotEmpty() }
            ?.map { value(it) }?.takeIf { values -> values.all { it != null && it.isFinite() && it >= 0 } }?.sumOf { it!! }.finite()
        val thrust = total { it.thrustKgf }
        val air = current.altitudeM?.let { StandardAtmosphere.atGeometricAltitude(it) }
        return FlightMetrics(
            observedEnginePeak = observedPeak.update(current, timeMs),
            cockpitAltitudeUnit = cockpitUnits.update(current, timeMs),
            energyHeightM = energy.finite(), accelerationMps2 = acceleration.finite(), specificExcessPowerMps = sep.finite(),
            estimatedTurnRadiusM = radius.finite(), estimatedTurnRateDegps = turnRate.finite(), fuelPercent = fuelPercent,
            fuelConsumptionKgPerMinute = fuel.consumptionKgPerMinute, fuelEnduranceSeconds = fuel.enduranceSeconds,
            totalPowerHp = total { it.powerHp }, totalThrustKgf = thrust,
            thrustPowerKw = (if (thrust != null && speed != null) thrust * G * speed / 1000 else null).finite(),
            standardDensityKgM3 = air?.densityKgM3,
            standardDynamicPressurePa = (if (air != null && speed != null) 0.5 * air.densityKgM3 * speed * speed else null).finite(),
        )
    }

    private fun Double?.finite() = this?.takeIf { it.isFinite() }

    companion object {
        const val G = 9.80665
        const val MAXIMUM_SAMPLE_GAP_MS = 2000L
    }
}
