package voidmei.telemetry

data class TemperatureReading(val value: Double, val sourceUnit: String, val engineIndex: Int? = null)

/** Legacy cockpit-first display order, without assuming cockpit values are degrees Celsius. */
fun Telemetry.displayTemperature(oil: Boolean): TemperatureReading? {
    fun reading(value: Double?, source: String) = value?.takeIf { it.isFinite() && it > -65534 }
        ?.let { TemperatureReading(it, source) }
    if (oil) reading(oilTemperatureRaw, "油温仪表原值")?.let { return it }
    else {
        reading(waterTemperatureRaw, "水温仪表原值")?.let { return it }
        reading(headTemperatureRaw, "缸温仪表原值")?.let { return it }
    }
    val first = engines.singleOrNull { it.index == 1 } ?: return null
    return reading(if (oil) first.oilTemperatureC else first.waterTemperatureC, "°C · 1号")?.copy(engineIndex = 1)
}
