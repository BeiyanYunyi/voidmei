package voidmei.telemetry

import voidmei.fm.EngineRpmReference
import voidmei.fm.EngineRpmLimit
import voidmei.fm.ControlEffectiveSpeeds
import voidmei.fm.LoadLimits
import voidmei.fm.WingLimits
import voidmei.fm.FlapLimits
import voidmei.fm.FlightModelParameters
import kotlin.math.exp

enum class AlertSeverity { WARNING, ADVISORY }

enum class FlightAlert(val voice: String, val label: String, val cooldownMs: Long,
    val severity: AlertSeverity = AlertSeverity.WARNING) {
    EMPTY_FUEL("fail_nofuel", "燃油耗尽", 60000),
    NEGATIVE_LOAD_LOW_THRUST("fail_engine", "负过载时发动机推力过低", 5000),
    LOW_RPM("warn_lowrpm", "发动机转速相对油门偏低（模型基准）", 10000),
    HIGH_RPM("warn_highrpm", "发动机达到模型转速上限", 10000),
    ENGINE_OVERHEAT("warn_engineoverheat", "高温工作预算不足 5 分钟（模型估算）", 60000),
    COMPRESSOR_STAGE("warn_compressor", "增压器档位偏离模型最优值", 3000, AlertSeverity.ADVISORY),
    LOW_FUEL_PRESSURE("warn_lowpressure", "燃油压力相对油门持续偏低", 30000),
    CRITICAL_AOA("aoaCrit", "接近临界迎角", 1000),
    HIGH_AOA("aoaHigh", "迎角偏高", 8000, AlertSeverity.ADVISORY),
    LOAD_LIMIT("warn_loadfactor", "超出基础质量估算过载限制", 2000),
    IAS_LIMIT("warn_ias", "接近模型速度限制", 10000),
    MACH_LIMIT("warn_mach", "接近模型马赫限制", 10000),
    STALL_SPEED("warn_stall", "低于基础质量估算失速 IAS", 2000),
    ALTITUDE_DESCENT("warn_altitude", "当前高度下下降过快", 5000),
    TERRAIN_CLOSURE("warn_terrain", "地形接近过快", 5000),
    FLAP_LIMIT("warn_flap", "襟翼接近或达到模型限制", 1000),
    GEAR_LIMIT("warn_gear", "起落架展开时达到模型速度限制", 7000),
    HIGH_DESCENT("warn_highvario", "起落架放下时下降率过高", 5000),
    AIRBRAKE_EXTENDED("warn_brake", "起落架未完全放下且减速板展开", 8000, AlertSeverity.ADVISORY),
    AILERON_EFFECTIVENESS("aileronEff", "达到副翼舵效衰减速度", 10000, AlertSeverity.ADVISORY),
    ELEVATOR_EFFECTIVENESS("elevatorEff", "达到升降舵舵效衰减速度", 10000, AlertSeverity.ADVISORY),
    RUDDER_EFFECTIVENESS("rudderEff", "达到方向舵舵效衰减速度", 10000, AlertSeverity.ADVISORY),
    LOW_FUEL("warn_lowfuel", "燃油不足 10%", 60000, AlertSeverity.ADVISORY),
}

data class AlertUpdate(val active: List<FlightAlert>, val voice: FlightAlert?, val voiceAttemptId: Long? = null)

data class AircraftAlertModel(val aircraft: String, val parameters: FlightModelParameters) {
    fun parametersFor(currentAircraft: String?): FlightModelParameters? =
        parameters.takeIf { currentAircraft != null && aircraft.equals(currentAircraft, ignoreCase = true) }
}

/** Pure per-flight decision state. Model-dependent rules require usable current-aircraft limits. */
class FlightAlerts {
    private val controlAlerts = setOf(FlightAlert.AILERON_EFFECTIVENESS, FlightAlert.ELEVATOR_EFFECTIVENESS, FlightAlert.RUDDER_EFFECTIVENESS)
    private val spokenControls = mutableSetOf<FlightAlert>()
    private var aircraft: String? = null
    private var previousTime: Long? = null
    private val lastVoiced = mutableMapOf<FlightAlert, Long>()
    private var lastUtterance: Long? = null
    private var previousRadio: Double? = null
    private var radioRate: Double? = null
    private var previousFlaps: Double? = null
    private val compressorSince = mutableMapOf<CompressorRecommendation, Long>()
    private var lowFuelPressureSince: Long? = null
    private data class VoiceAttempt(val id: Long, val alert: FlightAlert, val timeMs: Long)
    private var nextVoiceAttemptId = 0L
    private var voiceAttempt: VoiceAttempt? = null
    fun reset() { compressorSince.clear(); lowFuelPressureSince = null; voiceAttempt = null; spokenControls.clear(); aircraft = null; previousTime = null; lastVoiced.clear(); lastUtterance = null; previousRadio = null; radioRate = null; previousFlaps = null }

    /** Undo only the current failed start; keep the global interval to bound retries. */
    fun voiceFailed(attemptId: Long) {
        val attempt = voiceAttempt?.takeIf { it.id == attemptId } ?: return
        voiceAttempt = null
        if (lastVoiced[attempt.alert] == attempt.timeMs) {
            lastVoiced.remove(attempt.alert)
            spokenControls.remove(attempt.alert)
        }
    }

    /** Resolve all model-dependent limits together, against the aircraft in this exact sample. */
    fun updateForAircraft(state: ConnectionState, model: AircraftAlertModel?, nowMs: Long,
        voiceEnabled: Boolean, disabledVoices: Set<String> = emptySet(), thermalObservation: EngineThermalObservation? = null,
        voiceAvailable: (FlightAlert) -> Boolean = { true }): AlertUpdate {
        val telemetry = (state as? ConnectionState.Flying)?.telemetry
        val matching = model?.parametersFor(telemetry?.aircraft)
        return update(state, matching?.limits(telemetry?.wingSweepRatio, telemetry?.flapsPercent), nowMs,
            voiceEnabled, disabledVoices, matching?.gearLimitKmh,
            matching?.flapLimits?.speedAt(telemetry?.flapsPercent), matching?.flapLimits, matching?.structuralLoad?.limits(telemetry?.fuelKg), matching?.controlSpeeds, matching?.stallSpeed?.speedKmh(telemetry?.fuelKg, telemetry?.flapsPercent, telemetry?.wingSweepRatio), voiceAvailable, matching?.engineRpmLimits.orEmpty(), matching?.engineRpmReferences.orEmpty(), thermalObservation?.warningEngines(state, model).orEmpty(),
            telemetry?.let { CompressorAdvice.recommendations(it, matching?.engineCompressors.orEmpty()) }.orEmpty())
    }

    fun update(state: ConnectionState, limits: WingLimits?, nowMs: Long, voiceEnabled: Boolean, disabledVoices: Set<String> = emptySet(), gearLimitKmh: Double? = null, flapLimitKmh: Double? = null, flapModel: FlapLimits? = null, loadLimits: LoadLimits? = null, controlSpeeds: ControlEffectiveSpeeds? = null, stallSpeedKmh: Double? = null, voiceAvailable: (FlightAlert) -> Boolean = { true }, engineRpmLimits: List<EngineRpmLimit> = emptyList(), engineRpmReferences: List<EngineRpmReference> = emptyList(), thermalWarningEngines: Set<Int> = emptySet(), compressorRecommendations: List<CompressorRecommendation> = emptyList()): AlertUpdate {
        if (state !is ConnectionState.Flying) { reset(); return AlertUpdate(emptyList(), null) }
        if (aircraft != state.telemetry.aircraft || previousTime?.let { nowMs < it } == true) reset()
        // A slow successful poll is not a new flight. Preserve voice cooldowns and
        // spoken control episodes, but do not infer motion across a sparse interval.
        if (previousTime?.let { nowMs - it > 2500 } == true) {
            compressorSince.clear()
            lowFuelPressureSince = null
            previousTime = null
            previousRadio = null
            radioRate = null
            previousFlaps = null
        }
        aircraft = state.telemetry.aircraft
        val elapsed = previousTime?.let { (nowMs - it) / 1000.0 }
        previousTime = nowMs
        val t = state.telemetry
        compressorSince.keys.retainAll(compressorRecommendations.toSet())
        compressorRecommendations.forEach { compressorSince.getOrPut(it) { nowMs } }
        // The cockpit pressure has no engine suffix. Only associate it when exactly one
        // valid engine is present; do not guess a gauge's engine on multi-engine aircraft.
        val pressure = t.fuelPressureRaw?.takeIf { it.isFinite() && it >= 0 }
        val throttle = t.engines.singleOrNull()?.takeIf { it.index > 0 }?.throttlePercent
            ?.takeIf { it.isFinite() && it >= 0 }
        val lowPressure = pressure != null && throttle != null && throttle - pressure * 10 > 2
        if (!lowPressure) lowFuelPressureSince = null
        else if (lowFuelPressureSince == null) lowFuelPressureSince = nowMs
        val currentFlaps = t.flapsPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
        val extendingFlaps = elapsed != null && elapsed > 0 && currentFlaps != null &&
            previousFlaps?.let { currentFlaps > it } == true
        previousFlaps = if (elapsed != null && elapsed <= 0) null else currentFlaps
        val radio = t.radioAltitudeRaw?.takeIf { it.isFinite() && it >= 0 }
        radioRate = if (radio != null && previousRadio != null && elapsed != null && elapsed > 0) {
            val derivative = (radio - previousRadio!!) / elapsed
            // One-second smoothing, independent of poll frequency. Units cancel in the warning ratio.
            val alpha = 1 - exp(-elapsed)
            ((radioRate ?: 0.0) * (1 - alpha) + derivative * alpha).takeIf { it.isFinite() }
        } else null
        previousRadio = radio
        val active = buildList {
            val fuel = t.fuelKg?.takeIf { it.isFinite() && it >= 0 }
            if (fuel == 0.0) add(FlightAlert.EMPTY_FUEL)
            if (EngineWarnings.lowThrustUnderNegativeLoad(t).isNotEmpty()) add(FlightAlert.NEGATIVE_LOAD_LOW_THRUST)
            if (EngineWarnings.highRpm(t, engineRpmLimits).isNotEmpty()) add(FlightAlert.HIGH_RPM)
            if (EngineWarnings.lowRpm(t, engineRpmReferences).isNotEmpty()) add(FlightAlert.LOW_RPM)
            if (thermalWarningEngines.isNotEmpty()) add(FlightAlert.ENGINE_OVERHEAT)
            if (lowFuelPressureSince?.let { nowMs - it >= 2000 } == true) add(FlightAlert.LOW_FUEL_PRESSURE)
            if (compressorSince.values.any { nowMs - it >= 3000 }) add(FlightAlert.COMPRESSOR_STAGE)
            val aoa = t.angleOfAttackDeg?.takeIf { it.isFinite() }
            val upper = limits?.maxAngleOfAttackDeg?.takeIf { it.isFinite() && it > 0 }
            val lower = limits?.minAngleOfAttackDeg?.takeIf { it.isFinite() && it < 0 }
            if (aoa != null && t.iasKmh?.let { it.isFinite() && it > 80 } == true) {
                // Positive AoA thresholds follow the original two-stage warning. Retain the
                // negative-model-limit guard added during migration, gated by the same IAS.
                if ((upper != null && aoa > upper - 1) || (lower != null && aoa <= lower * 0.95))
                    add(FlightAlert.CRITICAL_AOA)
                else if (upper != null && aoa > upper * 0.75) add(FlightAlert.HIGH_AOA)
            }
            val load = t.loadG?.takeIf { it.isFinite() }
            if (load != null && loadLimits != null && loadLimits.minimumG.isFinite() && loadLimits.minimumG < 0 &&
                loadLimits.maximumG.isFinite() && loadLimits.maximumG > 0 &&
                (load < loadLimits.minimumG || load > loadLimits.maximumG)) add(FlightAlert.LOAD_LIMIT)
            val vne = limits?.vneKmh?.takeIf { it.isFinite() && it > 0 }
            if (vne != null && t.iasKmh?.let { it.isFinite() && it >= 0.95 * vne } == true) add(FlightAlert.IAS_LIMIT)
            val mne = limits?.maxMach?.takeIf { it.isFinite() && it > 0 }
            if (mne != null && t.mach?.let { it.isFinite() && it >= 0.95 * mne } == true) add(FlightAlert.MACH_LIMIT)
            val gear = t.gearPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
            val brake = t.airbrakePercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
            val descent = t.verticalSpeedMps?.takeIf { it.isFinite() }
            if (gear == 0.0 && descent != null && descent != 0.0 &&
                stallSpeedKmh != null && stallSpeedKmh.isFinite() && stallSpeedKmh > 0 &&
                t.iasKmh?.let { it.isFinite() && it >= 0 && it <= stallSpeedKmh } == true) add(FlightAlert.STALL_SPEED)
            if (gear == 0.0) {
                val height = t.altitudeM?.takeIf { it.isFinite() && it >= 0 }
                if (height != null && descent != null && descent < -height / 10)
                    add(FlightAlert.ALTITUDE_DESCENT)
                else if (radio != null && radio > 0 && radioRate?.let { it < -radio / 10 } == true)
                    add(FlightAlert.TERRAIN_CLOSURE)
            }
            val flaps = t.flapsPercent?.takeIf { it.isFinite() && it > 0 && it <= 100 }
            val flapLimit = flapLimitKmh?.takeIf { it.isFinite() && it > 0 }
            val overFlapLimit = flaps != null && flapLimit != null && t.iasKmh?.let { it.isFinite() && it >= flapLimit } == true
            val nearFlapLimit = flapModel?.isNearLimit(flaps, t.iasKmh, if (extendingFlaps) 8.0 else 2.0) == true
            if (overFlapLimit || nearFlapLimit) add(FlightAlert.FLAP_LIMIT)
            val gearLimit = gearLimitKmh?.takeIf { it.isFinite() && it > 0 }
            if (gear != null && gear > 0 && gearLimit != null &&
                t.iasKmh?.let { it.isFinite() && it >= gearLimit } == true) add(FlightAlert.GEAR_LIMIT)
            if (gear != null && gear >= 50 && descent != null && descent <= -8) add(FlightAlert.HIGH_DESCENT)
            val ias = t.iasKmh?.takeIf { it.isFinite() && it >= 0 }
            fun controlThreshold(speed: Double?, alert: FlightAlert) {
                if (ias != null && speed != null && speed.isFinite() && speed > 0 && ias >= speed) add(alert)
            }
            controlThreshold(controlSpeeds?.aileronKmh, FlightAlert.AILERON_EFFECTIVENESS)
            controlThreshold(controlSpeeds?.elevatorKmh, FlightAlert.ELEVATOR_EFFECTIVENESS)
            controlThreshold(controlSpeeds?.rudderKmh, FlightAlert.RUDDER_EFFECTIVENESS)
            val capacity = t.fuelCapacityKg?.takeIf { it.isFinite() && it > 0 }
            if (fuel != null && fuel > 0 && capacity != null && fuel / capacity <= 0.1) add(FlightAlert.LOW_FUEL)
            if (gear != null && gear < 100 && brake != null && brake >= 90) add(FlightAlert.AIRBRAKE_EXTENDED)
        }.sortedBy { it.severity }
        // Critical conditions suppress the lower-tier voice for eight seconds after recovery,
        // including when another warning took priority or audio was disabled.
        if (FlightAlert.CRITICAL_AOA in active) lastVoiced[FlightAlert.HIGH_AOA] = nowMs
        // Keep unspoken active crossings pending through the global voice interval.
        // A spoken control warning rearms only after its condition clears.
        spokenControls.retainAll(active.toSet())
        val voice = if (voiceEnabled && lastUtterance?.let { nowMs - it >= 2000 } != false)
            active.firstOrNull { alert -> alert !in spokenControls && alert.voice !in disabledVoices && voiceAvailable(alert) && (lastVoiced[alert]?.let { nowMs - it >= alert.cooldownMs } != false) } else null
        val attemptId = if (voice != null) {
            lastVoiced[voice] = nowMs
            lastUtterance = nowMs
            if (voice in controlAlerts) spokenControls += voice
            (++nextVoiceAttemptId).also { voiceAttempt = VoiceAttempt(it, voice, nowMs) }
        } else null
        return AlertUpdate(active, voice, attemptId)
    }
}
