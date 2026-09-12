package voidmei.fm

/** Seconds of model work budget per model recovery second, not live thermal recovery. */
fun EngineThermalBand.recoveryRate(): Double? {
    val work = workSeconds?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val recovery = recoverSeconds?.takeIf { it.isFinite() && it > 0 } ?: return null
    return (work / recovery).takeIf { it.isFinite() }
}

data class ThermalRecoverySummary(val validBands: Int, val totalBands: Int, val meanRate: Double?)
fun EngineThermalParameters.recoverySummary(): ThermalRecoverySummary {
    val rates = bands.mapNotNull { it.recoveryRate() }
    // Divide first so multiple large finite rates do not overflow their sum.
    val mean = if (rates.isEmpty()) null else rates.sumOf { it / rates.size }.takeIf { it.isFinite() }
    return ThermalRecoverySummary(rates.size, bands.size, mean)
}
