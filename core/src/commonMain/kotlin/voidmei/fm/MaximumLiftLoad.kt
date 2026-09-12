package voidmei.fm

/** Quasi-steady lift/mass ratio using the same IAS convention as the 1 G stall model. */
fun StallSpeedModel.maximumLiftLoadAtIas(iasKmh: Double?, fuelKg: Double?, flapsPercent: Double?, sweep: Double?): Double? {
    if (iasKmh == null || !iasKmh.isFinite() || iasKmh < 0) return null
    val stall = speedKmh(fuelKg, flapsPercent, sweep)?.takeIf { it.isFinite() && it > 0 } ?: return null
    val ratio = iasKmh / stall
    return (ratio * ratio).takeIf { it.isFinite() }
}
