package voidmei.telemetry

/** Explicit replacement for the legacy gear/speed/throttle trigger; unknowns never trigger. */
fun Telemetry.triggersJetWindowDismissal(): Boolean {
    val gear = gearPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
    if (gear != null && gear < 100) return true
    val speed = tasKmh?.takeIf { it.isFinite() && it >= 0 } ?: return false
    return speed > 36 && engines.any { engine -> engine.throttlePercent?.let { it.isFinite() && it > 0 } == true }
}
