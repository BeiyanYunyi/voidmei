package voidmei.telemetry

/** NIST conventional pressure factors; boost uses a fixed 1 atm reference, not local ambient pressure. */
enum class ManifoldPressureUnit {
    ATM, INHG, BOOST_PSI;

    fun fromAtm(value: Double?): Double? {
        val pressure = value?.takeIf { it.isFinite() && it >= 0 } ?: return null
        return when (this) {
            ATM -> pressure
            INHG -> pressure * (101325.0 / 3386.389)
            BOOST_PSI -> (pressure - 1) * (101325.0 / 6894.757)
        }.takeIf { it.isFinite() }
    }
}
