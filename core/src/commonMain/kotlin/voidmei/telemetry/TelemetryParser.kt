package voidmei.telemetry

import kotlinx.serialization.json.*

/** Exact JSON keys prevent collisions such as Mfuel/Mfuel0 and engine 1/engine 10. */
object TelemetryParser {
    private val engineKey = Regex("(.+) ([1-9][0-9]*)(, .+)?")
    private val engineUnits = mapOf(
        "throttle" to ", %", "RPM" to "", "power" to ", hp", "thrust" to ", kgs",
        "water temp" to ", C", "oil temp" to ", C", "RPM throttle" to ", %",
        "mixture" to ", %", "radiator" to ", %", "oil radiator" to ", %",
        "compressor stage" to "", "magneto" to "", "manifold pressure" to ", atm",
        "pitch" to ", deg", "efficiency" to ", %",
    )

    fun parse(stateBody: String, indicatorsBody: String): Telemetry? {
        val state = Json.parseToJsonElement(stateBody).jsonObject
        val indicators = Json.parseToJsonElement(indicatorsBody).jsonObject
        if (!state.valid() || !indicators.valid()) return null
        val army = indicators.string("army")
        if (army != null && army != "air") return null
        val engines = state.keys.mapNotNull {
            val match = engineKey.matchEntire(it) ?: return@mapNotNull null
            val unit = engineUnits[match.groupValues[1]] ?: return@mapNotNull null
            if (match.groupValues[3] != unit) return@mapNotNull null
            match.groupValues[2].toIntOrNull()
        }.distinct().sorted().map { index ->
                Engine(index, state.number("throttle $index, %"), state.number("RPM $index"),
                    state.number("power $index, hp"), state.number("thrust $index, kgs"),
                    state.number("water temp $index, C"), state.number("oil temp $index, C"),
                    rpmControlPercent = state.number("RPM throttle $index, %"),
                    mixturePercent = state.number("mixture $index, %"),
                    radiatorPercent = state.number("radiator $index, %"),
                    oilRadiatorPercent = state.number("oil radiator $index, %"),
                    compressorStage = state.number("compressor stage $index"),
                    magneto = state.number("magneto $index"),
                    manifoldPressureAtm = state.number("manifold pressure $index, atm"),
                    propellerPitchDeg = state.number("pitch $index, deg"),
                    efficiencyPercent = state.number("efficiency $index, %"))
            }
        return Telemetry(
            aircraft = indicators.string("type"),
            iasKmh = state.number("IAS, km/h"), tasKmh = state.number("TAS, km/h"),
            mach = state.number("M"), altitudeM = state.number("H, m"),
            verticalSpeedMps = state.number("Vy, m/s"), loadG = state.number("Ny"),
            angleOfAttackDeg = state.number("AoA, deg"), fuelKg = state.number("Mfuel, kg"),
            fuelCapacityKg = state.number("Mfuel0, kg"), gearPercent = state.number("gear, %"),
            flapsPercent = state.number("flaps, %"), airbrakePercent = state.number("airbrake, %"),
            aileronPercent = state.number("aileron, %"), elevatorPercent = state.number("elevator, %"),
            rudderPercent = state.number("rudder, %"), rollDeg = indicators.number("aviahorizon_roll"),
            pitchDeg = indicators.number("aviahorizon_pitch"), headingDeg = indicators.number("compass"),
            engines = engines,
            wingSweepRatio = indicators.number("wing_sweep_indicator"),
            radioAltitudeRaw = indicators.number("radio_altitude")?.takeIf { it >= 0 },
            sideslipAngleDeg = state.number("AoS, deg"),
            rollRateDegPerSecond = state.number("Wx, deg/s"),
            fuelPressureRaw = indicators.number("fuel_pressure")?.takeIf { it >= 0 },
            oilPressureRaw = indicators.number("oil_pressure")?.takeIf { it >= 0 },
            waterTemperatureRaw = indicators.number("water_temperature")?.takeIf { it > -65534 },
            headTemperatureRaw = indicators.number("head_temperature")?.takeIf { it > -65534 },
            oilTemperatureRaw = indicators.number("oil_temperature")?.takeIf { it > -65534 },
            altimeterRaw = indicators.number("altitude_10k"),
            boosterFuelKg = state.uniqueNumber("Mfuel 1, kg", "Mfuel 1")?.takeIf { it >= 0 },
            boosterFuelCapacityKg = state.uniqueNumber("Mfuel0 1, kg", "Mfuel0 1")?.takeIf { it >= 0 },
        )
    }

    private fun JsonObject.valid() = (get("valid") as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == true } == true
    private fun JsonObject.uniqueNumber(vararg keys: String): Double? =
        keys.filter { containsKey(it) }.singleOrNull()?.let { number(it) }

    private fun JsonObject.number(key: String): Double? = (get(key) as? JsonPrimitive)
        ?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it != -65535.0 }
    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
