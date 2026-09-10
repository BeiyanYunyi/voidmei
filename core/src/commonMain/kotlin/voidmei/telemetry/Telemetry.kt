package voidmei.telemetry

/** All values retain the units used by the 8111 API. Absent values are never zero-filled. */
data class Engine(
    val index: Int,
    val throttlePercent: Double?,
    val rpm: Double?,
    val powerHp: Double?,
    val thrustKgf: Double?,
    val waterTemperatureC: Double?,
    val oilTemperatureC: Double?,
    val rpmControlPercent: Double? = null,
    val mixturePercent: Double? = null,
    val radiatorPercent: Double? = null,
    val oilRadiatorPercent: Double? = null,
    val compressorStage: Double? = null,
    val magneto: Double? = null,
    val manifoldPressureAtm: Double? = null,
    val propellerPitchDeg: Double? = null,
    val efficiencyPercent: Double? = null,
)

data class Telemetry(
    val aircraft: String?,
    val iasKmh: Double?,
    val tasKmh: Double?,
    val mach: Double?,
    val altitudeM: Double?,
    val verticalSpeedMps: Double?,
    val loadG: Double?,
    val angleOfAttackDeg: Double?,
    val fuelKg: Double?,
    val fuelCapacityKg: Double?,
    val gearPercent: Double?,
    val flapsPercent: Double?,
    val airbrakePercent: Double?,
    val aileronPercent: Double?,
    val elevatorPercent: Double?,
    val rudderPercent: Double?,
    val rollDeg: Double?,
    val pitchDeg: Double?,
    val headingDeg: Double?,
    val engines: List<Engine>,
    val wingSweepRatio: Double? = null,
    /** Cockpit units may be metres or feet; never label this raw value as metres. */
    val radioAltitudeRaw: Double? = null,
    /** Unnumbered cockpit gauge; units and multi-engine association are not established. */
    val fuelPressureRaw: Double? = null,
    val sideslipAngleDeg: Double? = null,
    val rollRateDegPerSecond: Double? = null,
    /** Unnumbered cockpit readings; units and engine association are not established. */
    val waterTemperatureRaw: Double? = null,
    val headTemperatureRaw: Double? = null,
    val oilTemperatureRaw: Double? = null,
    /** Cockpit altimeter reading used only for scale inference; never assumed to be metres. */
    val altimeterRaw: Double? = null,
    val boosterFuelKg: Double? = null,
    val boosterFuelCapacityKg: Double? = null,
)

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object WaitingForFlight : ConnectionState
    data class Flying(val telemetry: Telemetry, val metrics: FlightMetrics) : ConnectionState
    data class Disconnected(val reason: String) : ConnectionState
}
