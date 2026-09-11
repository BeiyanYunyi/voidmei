package voidmei.telemetry

/** Signed control percentage; unavailable values must not become a neutral position. */
fun controlSurfacePercent(value: Double?): Double? = value?.takeIf { it.isFinite() && it in -100.0..100.0 }
