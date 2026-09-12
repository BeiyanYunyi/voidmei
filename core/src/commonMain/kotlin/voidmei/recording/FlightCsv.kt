package voidmei.recording

import voidmei.telemetry.*

/** Stable column names include units; absent/non-finite numeric values are empty cells. */
object FlightCsv {
    val flightHeader = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,tas_kmh,mach,altitude_m,vertical_speed_mps,load_g,aoa_deg,fuel_kg,fuel_capacity_kg,gear_percent,flaps_percent,airbrake_percent,aileron_percent,elevator_percent,rudder_percent,roll_deg,pitch_deg,heading_deg,wing_sweep_ratio,energy_height_m,acceleration_mps2,sep_mps,turn_radius_m,turn_rate_degps,fuel_kg_min,endurance_s,total_power_hp,total_thrust_kgf,thrust_power_kw,radio_altitude_raw,fuel_pressure_raw,sideslip_deg,roll_rate_degps,water_temperature_raw,head_temperature_raw,oil_temperature_raw,altimeter_raw,engine_response_percent_per_s,booster_fuel_kg,booster_fuel_capacity_kg,wep_fuel_upper_kg,wep_time_upper_s,oil_pressure_raw"
    val engineHeader = "sample_id,utc_epoch_ms,engine_index,throttle_percent,rpm,power_hp,thrust_kgf,water_temp_c,oil_temp_c,rpm_control_percent,mixture_percent,radiator_percent,oil_radiator_percent,compressor_stage,magneto,manifold_pressure_atm,propeller_pitch_deg,efficiency_percent"

    fun flightRow(id: Long, epochMs: Long, elapsedMs: Long, flight: ConnectionState.Flying): String {
        val t = flight.telemetry
        val m = flight.metrics
        val wep = m.wepFuel?.takeIf { it.telemetry == t }?.estimate
        return (listOf(id.toString(), epochMs.toString(), elapsedMs.toString(), text(t.aircraft.orEmpty())) +
            listOf(t.iasKmh, t.tasKmh, t.mach, t.altitudeM, t.verticalSpeedMps, t.loadG, t.angleOfAttackDeg,
                t.fuelKg, t.fuelCapacityKg, t.gearPercent, t.flapsPercent, t.airbrakePercent,
                t.aileronPercent, t.elevatorPercent, t.rudderPercent, t.rollDeg, t.pitchDeg, t.headingDeg,
                t.wingSweepRatio, m.energyHeightM, m.accelerationMps2, m.specificExcessPowerMps,
                m.estimatedTurnRadiusM, m.estimatedTurnRateDegps, m.fuelConsumptionKgPerMinute,
                m.fuelEnduranceSeconds, m.totalPowerHp, m.totalThrustKgf, m.thrustPowerKw, t.radioAltitudeRaw, t.fuelPressureRaw, t.sideslipAngleDeg, t.rollRateDegPerSecond,
                t.waterTemperatureRaw, t.headTemperatureRaw, t.oilTemperatureRaw, t.altimeterRaw, m.engineResponsePercentPerSecond, t.boosterFuelKg, t.boosterFuelCapacityKg, wep?.maximumRemainingKg, wep?.maximumSecondsAtCurrentRate, t.oilPressureRaw?.takeIf { it >= 0 }).map(::number)).joinToString(",")
    }

    fun engineRows(id: Long, epochMs: Long, engines: List<Engine>): List<String> = engines.map {
        (listOf(id.toString(), epochMs.toString(), it.index.toString()) + listOf(it.throttlePercent,
            it.rpm, it.powerHp, it.thrustKgf, it.waterTemperatureC, it.oilTemperatureC,
            it.rpmControlPercent, it.mixturePercent, it.radiatorPercent, it.oilRadiatorPercent,
            it.compressorStage, it.magneto, it.manifoldPressureAtm, it.propellerPitchDeg, it.efficiencyPercent).map(::number)).joinToString(",")
    }

    private fun number(value: Double?) = value?.takeIf { it.isFinite() }?.toString().orEmpty()
    private fun text(value: String): String {
        // Spreadsheet imports must not execute aircraft identifiers as formulas.
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
