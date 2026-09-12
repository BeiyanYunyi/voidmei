package voidmei.fm

/** Raw FM coefficients; not percentages or a measurement of current control authority. */
data class ControlPowerLoss(val aileron: Double? = null, val elevator: Double? = null, val rudder: Double? = null) {
    init { require(listOf(aileron, elevator, rudder).all { it == null || it.isFinite() }) }
}
