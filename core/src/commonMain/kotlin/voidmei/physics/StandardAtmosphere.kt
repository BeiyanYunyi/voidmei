package voidmei.physics

import kotlin.math.*

data class AirProperties(val temperatureK: Double, val pressurePa: Double, val densityKgM3: Double) {
    val speedOfSoundMps: Double get() = sqrt(1.4 * StandardAtmosphere.R * temperatureK)
}

/** Dry standard atmosphere, -4 to 32 km geopotential altitude. Not the game's weather model.
 * Layer definitions: NASA/TM-2005-213659, table 1.
 */
object StandardAtmosphere {
    const val G = 9.80665
    const val R = 287.05287
    private const val T0 = 288.15
    private const val P0 = 101325.0
    private const val LAPSE = -0.0065
    private const val EARTH_RADIUS_M = 6356766.0
    private val p11 = P0 * (216.65 / T0).pow(-G / (R * LAPSE))
    private val p20 = p11 * exp(-G * 9000 / (R * 216.65))

    fun atGeometricAltitude(altitudeM: Double): AirProperties? {
        if (!altitudeM.isFinite() || altitudeM <= -EARTH_RADIUS_M) return null
        return atGeopotentialAltitude(EARTH_RADIUS_M * altitudeM / (EARTH_RADIUS_M + altitudeM))
    }

    fun atGeopotentialAltitude(altitudeM: Double): AirProperties? {
        if (!altitudeM.isFinite() || altitudeM !in -4000.0..32000.0) return null
        val temperature: Double
        val pressure: Double
        when {
            altitudeM <= 11000 -> {
                temperature = T0 + LAPSE * altitudeM
                pressure = P0 * (temperature / T0).pow(-G / (R * LAPSE))
            }
            altitudeM <= 20000 -> {
                temperature = 216.65
                pressure = p11 * exp(-G * (altitudeM - 11000) / (R * temperature))
            }
            else -> {
                temperature = 216.65 + 0.001 * (altitudeM - 20000)
                pressure = p20 * (temperature / 216.65).pow(-G / (R * 0.001))
            }
        }
        return AirProperties(temperature, pressure, pressure / (R * temperature))
    }

    fun geopotentialAltitudeAtPressure(pressurePa: Double): Double? {
        if (!pressurePa.isFinite() || pressurePa <= 0) return null
        val altitude = when {
            pressurePa >= p11 -> T0 / LAPSE * ((pressurePa / P0).pow(-R * LAPSE / G) - 1)
            pressurePa >= p20 -> 11000 - R * 216.65 / G * ln(pressurePa / p11)
            else -> 20000 + 216.65 / 0.001 * ((pressurePa / p20).pow(-R * 0.001 / G) - 1)
        }
        return altitude.takeIf { it in -4000.000001..32000.000001 }?.coerceIn(-4000.0, 32000.0)
    }

    /** Equivalent airspeed, not compressibility-corrected IAS. */
    fun equivalentAirspeedKmh(tasKmh: Double, densityKgM3: Double): Double? =
        if (tasKmh.isFinite() && tasKmh >= 0 && densityKgM3.isFinite() && densityKgM3 > 0)
            tasKmh * sqrt(densityKgM3 / 1.225) else null
}
