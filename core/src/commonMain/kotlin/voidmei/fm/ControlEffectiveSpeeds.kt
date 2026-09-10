package voidmei.fm

/** FM indicated speeds where control effectiveness starts to decrease, in km/h. */
data class ControlEffectiveSpeeds(
    val aileronKmh: Double? = null,
    val elevatorKmh: Double? = null,
    val rudderKmh: Double? = null,
)
