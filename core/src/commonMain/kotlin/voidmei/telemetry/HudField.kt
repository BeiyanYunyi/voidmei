package voidmei.telemetry

/** Stable persisted IDs, independent of display language and UI toolkit. */
enum class HudField(val id: String, val label: String, val unit: String, val decimals: Int) {
    IAS("ias", "IAS", "km/h", 0), ALTITUDE("altitude", "高度", "m", 0),
    TAS("tas", "TAS", "km/h", 0), CLIMB("climb", "爬升", "m/s", 1),
    MACH("mach", "Mach", "", 2), LOAD("load", "过载", "G", 1),
    AOA("aoa", "迎角", "°", 1), FUEL("fuel", "燃油", "kg", 0),
    SEP("sep", "SEP", "m/s", 1), ENERGY("energy", "能量高度", "m", 0),
    FUEL_PERCENT("fuel_percent", "燃油量", "%", 1), ENDURANCE("endurance", "续航估计", "min", 1),
    ACCELERATION("acceleration", "加速度", "m/s²", 2), TURN_RATE("turn_rate", "转弯率估计", "°/s", 1),
    TURN_RADIUS("turn_radius", "转弯半径估计", "m", 0), THRUST("thrust", "总推力", "kgf", 0),
    POWER("power", "总功率", "hp", 0),
    RADIO_ALTITUDE_ESTIMATE("radio_altitude_estimate", "雷达高度估计", "m", 0),
    RADIO_ALTITUDE_RAW("radio_altitude_raw", "雷达高度原值", "仪表单位", 0),
    STALL_IAS("stall_ias", "1 G 失速IAS", "km/h", 0),
    SIDESLIP("sideslip", "侧滑角", "°", 1),
    ROLL_RATE("roll_rate", "滚转角速度", "°/s", 1),
    HEADING("heading", "航向", "°", 0),
    AILERON("aileron", "副翼", "%", 1),
    ELEVATOR("elevator", "升降舵", "%", 1),
    RUDDER("rudder", "方向舵", "%", 1),
    WING_SWEEP("wing_sweep", "后掠", "%", 1),
    SPEED_LIMIT_RATIO("speed_limit_ratio", "模型速度限制比例", "%", 1),
    ENGINE1_THROTTLE("engine1_throttle", "1 号油门", "%", 0),
    ENGINE1_THRUST("engine1_thrust", "1 号推力", "kgf", 0),
    ENGINE1_RPM("engine1_rpm", "1 号转速", "RPM", 0),
    ENGINE1_PITCH("engine1_pitch", "1 号桨距", "°", 1),
    THRUST_POWER("thrust_power", "推进功率", "kW", 1),
    ENDURANCE_CLOCK("endurance_clock", "续航时间（分:秒）", "", 0),
    FUEL_MASS_SHARE("fuel_mass_share", "燃油质量占比估计", "%", 1),
    MASS_ESTIMATE("mass_estimate", "质量估计", "kg", 0),
    ENGINE_TEMPERATURE("engine_temperature", "发动机温度", "", 1),
    OIL_TEMPERATURE("oil_temperature", "滑油温度", "", 1),
    ENGINE1_MANIFOLD_ATM("engine1_manifold_atm", "1 号进气压力（atm）", "atm", 2),
    ENGINE1_MANIFOLD_INHG("engine1_manifold_inhg", "1 号进气压力（inHg）", "inHg", 1),
    ENGINE1_BOOST_PSI("engine1_boost_psi", "1 号增压（相对1atm）", "psi", 1),
    ENGINE1_MANIFOLD_AUTO("engine1_manifold_auto", "1 号进气压力（自动推断）", "", 2),
    PROPULSIVE_EFFICIENCY("propulsive_efficiency", "推进效率估计", "%", 1),
    POWER_PERCENT("power_percent", "动力量", "%", 0),
    HEAT_TOLERANCE("heat_tolerance", "1 号热预算估计", "s", 1),
    ENGINE_RESPONSE("engine_response", "动力量响应", "%/s", 1),
    BOOSTER_FUEL("booster_fuel", "助推燃料（通道 1）", "kg", 1),
    BOOSTER_FUEL_PERCENT("booster_fuel_percent", "助推燃料余量", "%", 0),
    WEP_FUEL("wep_fuel", "WEP 燃料上限", "kg", 1),
    WEP_TIME("wep_time", "WEP 续航上限（分:秒）", "", 0),
    FUEL_LOSS_RATE("fuel_loss_rate", "燃油减少率估计", "kg/min", 1),
    FUEL_PRESSURE_RAW("fuel_pressure_raw", "燃油压力原值", "仪表单位", 2),
    PITCH("pitch", "俯仰（抬头为正）", "°", 1),
    ROLL("roll", "横滚", "°", 1);

    fun decimalsFor(metrics: FlightMetrics): Int =
        if (this == ENGINE1_MANIFOLD_AUTO && metrics.cockpitAltitudeUnit == CockpitAltitudeUnit.FEET) 1 else decimals

    fun unitFor(telemetry: Telemetry, metrics: FlightMetrics? = null, model: AircraftAlertModel? = null): String = when (this) {
        RADIO_ALTITUDE_ESTIMATE -> if (metrics?.cockpitAltitudeUnit == null) "m · 待判定" else "m · 按高度表单位推断"
        POWER_PERCENT -> metrics?.let { ConnectionState.Flying(telemetry, it).powerPercentReading(model) }
            ?.let { "% · ${it.source}" } ?: "%"
        ENGINE1_MANIFOLD_AUTO -> when (metrics?.cockpitAltitudeUnit) {
            CockpitAltitudeUnit.METRES -> "atm"
            CockpitAltitudeUnit.FEET -> {
                val pressure = telemetry.engines.singleOrNull { it.index == 1 }?.manifoldPressureAtm
                val inches = ManifoldPressureUnit.INHG.fromAtm(pressure)
                if (inches == null) "psi" else "psi · ${(if (inches <= Double.MAX_VALUE / 10) kotlin.math.round(inches * 10) / 10 else inches)} inHg"
            }
            null -> "待判定"
        }
        ENGINE_TEMPERATURE -> telemetry.displayTemperature(false)?.sourceUnit.orEmpty()
        OIL_TEMPERATURE -> telemetry.displayTemperature(true)?.sourceUnit.orEmpty()
        else -> unit
    }

    fun value(flight: ConnectionState.Flying, model: AircraftAlertModel? = null): Double? = when (this) {
        RADIO_ALTITUDE_ESTIMATE -> {
            val raw = flight.telemetry.radioAltitudeRaw?.takeIf { it.isFinite() && it >= 0 }
            when (flight.metrics.cockpitAltitudeUnit) {
                CockpitAltitudeUnit.METRES -> raw
                CockpitAltitudeUnit.FEET -> raw?.times(0.3048)
                null -> null
            }
        }
        ENGINE1_MANIFOLD_AUTO -> {
            val pressure = flight.telemetry.engines.singleOrNull { it.index == 1 }?.manifoldPressureAtm
            when (flight.metrics.cockpitAltitudeUnit) {
                CockpitAltitudeUnit.METRES -> ManifoldPressureUnit.ATM.fromAtm(pressure)
                CockpitAltitudeUnit.FEET -> ManifoldPressureUnit.BOOST_PSI.fromAtm(pressure)
                null -> null
            }
        }
        ENGINE1_MANIFOLD_ATM, ENGINE1_MANIFOLD_INHG, ENGINE1_BOOST_PSI -> {
            val pressure = flight.telemetry.engines.singleOrNull { it.index == 1 }?.manifoldPressureAtm
            val unit = when (this) {
                ENGINE1_MANIFOLD_INHG -> ManifoldPressureUnit.INHG
                ENGINE1_BOOST_PSI -> ManifoldPressureUnit.BOOST_PSI
                else -> ManifoldPressureUnit.ATM
            }
            unit.fromAtm(pressure)
        }
        ENGINE_TEMPERATURE -> flight.telemetry.displayTemperature(false)?.value
        OIL_TEMPERATURE -> flight.telemetry.displayTemperature(true)?.value
        FUEL_MASS_SHARE -> model?.parametersFor(flight.telemetry.aircraft)?.basicMassKg
            ?.takeIf { it.isFinite() && it > 0 }?.let { basic ->
                flight.telemetry.fuelKg?.takeIf { it.isFinite() && it >= 0 }?.let { fuel ->
                    (basic + fuel).takeIf { it.isFinite() }?.let { fuel / it * 100 }
                }
            }
        MASS_ESTIMATE -> model?.parametersFor(flight.telemetry.aircraft)?.basicMassKg
            ?.takeIf { it.isFinite() && it > 0 }?.let { basic ->
                flight.telemetry.fuelKg?.takeIf { it.isFinite() && it >= 0 }?.plus(basic)
            }
        ENDURANCE_CLOCK -> flight.metrics.fuelEnduranceSeconds?.takeIf { it >= 0 }
        WEP_FUEL -> flight.metrics.wepFuel?.estimateFor(flight, model)?.maximumRemainingKg
        WEP_TIME -> flight.metrics.wepFuel?.estimateFor(flight, model)?.maximumSecondsAtCurrentRate
        BOOSTER_FUEL -> flight.telemetry.boosterFuelKg?.takeIf { it >= 0 }
        BOOSTER_FUEL_PERCENT -> {
            val fuel = flight.telemetry.boosterFuelKg?.takeIf { it.isFinite() && it >= 0 }
            val capacity = flight.telemetry.boosterFuelCapacityKg?.takeIf { it.isFinite() && it > 0 }
            if (fuel != null && capacity != null) (fuel / capacity).coerceIn(0.0, 1.0) * 100 else null
        }
        ENGINE_RESPONSE -> flight.metrics.engineResponsePercentPerSecond
        HEAT_TOLERANCE -> null // Range rendered from the matching thermal observation.
        POWER_PERCENT -> flight.powerPercentReading(model)?.percent
        PROPULSIVE_EFFICIENCY -> {
            // Preserve the legacy 735 W/hp reference without truncating either power sum.
            val shaft = flight.metrics.totalPowerHp?.takeIf { it.isFinite() && it > 0 }
            val useful = flight.metrics.thrustPowerKw?.takeIf { it.isFinite() && it >= 0 }
            if (shaft != null && useful != null) useful / shaft / 0.735 * 100 else null
        }
        THRUST_POWER -> flight.metrics.thrustPowerKw
        SPEED_LIMIT_RATIO -> SpeedLimitScale.fromFlight(flight, model)?.ratio?.times(100)
        ENGINE1_THROTTLE -> flight.telemetry.engines.singleOrNull { it.index == 1 }?.throttlePercent?.takeIf { it >= 0 }
        ENGINE1_THRUST -> flight.telemetry.engines.singleOrNull { it.index == 1 }?.thrustKgf?.takeIf { it >= 0 }
        ENGINE1_RPM -> flight.telemetry.engines.singleOrNull { it.index == 1 }?.rpm?.takeIf { it >= 0 }
        ENGINE1_PITCH -> flight.telemetry.engines.singleOrNull { it.index == 1 }?.propellerPitchDeg
        WING_SWEEP -> flight.telemetry.wingSweepRatio?.takeIf { it in 0.0..1.0 }?.times(100)
        AILERON -> flight.telemetry.aileronPercent
        ELEVATOR -> flight.telemetry.elevatorPercent
        RUDDER -> flight.telemetry.rudderPercent
        HEADING -> AttitudeGeometry.heading(flight.telemetry.headingDeg)?.let { kotlin.math.round(it) % 360 }
        PITCH -> AttitudeGeometry.fromIndicators(flight.telemetry.pitchDeg, 0.0)?.pitchDeg
        ROLL -> AttitudeGeometry.fromIndicators(0.0, flight.telemetry.rollDeg)?.rollDeg
        SIDESLIP -> flight.telemetry.sideslipAngleDeg
        ROLL_RATE -> flight.telemetry.rollRateDegPerSecond
        STALL_IAS -> model?.parametersFor(flight.telemetry.aircraft)?.stallSpeed?.speedKmh(
            flight.telemetry.fuelKg, flight.telemetry.flapsPercent, flight.telemetry.wingSweepRatio)
        RADIO_ALTITUDE_RAW -> flight.telemetry.radioAltitudeRaw
        IAS -> flight.telemetry.iasKmh
        ALTITUDE -> flight.telemetry.altitudeM
        TAS -> flight.telemetry.tasKmh
        CLIMB -> flight.telemetry.verticalSpeedMps
        MACH -> flight.telemetry.mach
        LOAD -> flight.telemetry.loadG
        AOA -> flight.telemetry.angleOfAttackDeg
        FUEL -> flight.telemetry.fuelKg
        SEP -> flight.metrics.specificExcessPowerMps
        ENERGY -> flight.metrics.energyHeightM
        FUEL_PERCENT -> flight.metrics.fuelPercent
        ENDURANCE -> flight.metrics.fuelEnduranceSeconds?.div(60)
        FUEL_LOSS_RATE -> flight.metrics.fuelConsumptionKgPerMinute?.takeIf { it >= 0 }
        FUEL_PRESSURE_RAW -> flight.telemetry.fuelPressureRaw?.takeIf { it >= 0 }
        ACCELERATION -> flight.metrics.accelerationMps2
        TURN_RATE -> flight.metrics.estimatedTurnRateDegps
        TURN_RADIUS -> flight.metrics.estimatedTurnRadiusM
        THRUST -> flight.metrics.totalThrustKgf
        POWER -> flight.metrics.totalPowerHp
    }?.takeIf { it.isFinite() }

    companion object {
        val defaults = entries.take(10).map { it.id }
        fun selected(ids: List<String>): List<HudField> = ids.distinct().mapNotNull { id -> entries.find { it.id == id } }
    }
}
