package voidmei.telemetry

import voidmei.fm.EngineRpmReference
import voidmei.fm.EngineRpmLimit

/** Observable symptom only: this does not establish engine damage or fuel-system failure. */
object EngineWarnings {
    fun lowRpm(telemetry: Telemetry, references: List<EngineRpmReference>): Set<Int> {
        val duplicates = telemetry.engines.groupingBy { it.index }.eachCount().filterValues { it > 1 }.keys
        val byIndex = references.groupBy { it.telemetryIndex }
        val invertedLowThrust = lowThrustUnderNegativeLoad(telemetry)
        return telemetry.engines.filter { engine ->
            val reference = byIndex[engine.index]?.singleOrNull() ?: return@filter false
            val type = reference.type.lowercase()
            val control = engine.rpmControlPercent
            val controlAvailable = type == "jet" || (type in listOf("inline", "radial", "piston") &&
                control != null && control.isFinite() && control >= 0)
            val rpm = engine.rpm
            val throttle = engine.throttlePercent
            engine.index > 0 && engine.index !in duplicates && engine.index !in invertedLowThrust && controlAvailable &&
                reference.nominalRpm.isFinite() && reference.nominalRpm > 0 &&
                rpm != null && rpm.isFinite() && rpm >= 0 && throttle != null && throttle.isFinite() &&
                throttle - 30 > rpm / reference.nominalRpm * 100
        }.map { it.index }.toSet()
    }

    fun highRpm(telemetry: Telemetry, limits: List<EngineRpmLimit>): Set<Int> {
        val duplicatedEngines = telemetry.engines.groupingBy { it.index }.eachCount().filterValues { it > 1 }.keys
        val byIndex = limits.groupBy { it.telemetryIndex }
        return telemetry.engines.filter { engine ->
            val maximum = byIndex[engine.index]?.singleOrNull()?.maximumRpm
            val rpm = engine.rpm
            engine.index > 0 && engine.index !in duplicatedEngines &&
                maximum != null && maximum.isFinite() && maximum > 0 && rpm != null && rpm.isFinite() && rpm >= maximum
        }.map { it.index }.toSet()
    }

    fun lowThrustUnderNegativeLoad(telemetry: Telemetry): Set<Int> {
        val load = telemetry.loadG?.takeIf { it.isFinite() } ?: return emptySet()
        if (load >= 0) return emptySet()
        val duplicated = telemetry.engines.groupingBy { it.index }.eachCount().filterValues { it > 1 }.keys
        return telemetry.engines.filter { engine ->
            val throttle = engine.throttlePercent
            val thrust = engine.thrustKgf
            engine.index > 0 && engine.index !in duplicated &&
                throttle != null && throttle.isFinite() && throttle > 50 &&
                thrust != null && thrust.isFinite() && thrust >= 0 && thrust < 50
        }.map { it.index }.toSet()
    }
}
