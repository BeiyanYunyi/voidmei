package voidmei.telemetry

/** Displays whole seconds, with minutes allowed to exceed 59; unknown is never zero-filled. */
fun formatFuelTime(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds < 0 || seconds >= Long.MAX_VALUE.toDouble()) return "—"
    val wholeSeconds = seconds.toLong()
    return "${(wholeSeconds / 60).toString().padStart(2, '0')}:${(wholeSeconds % 60).toString().padStart(2, '0')}"
}

/** Rounds a nonnegative bound outwards at the displayed precision. */
fun roundUpperBound(value: Double?, decimals: Int): Double? {
    require(decimals in 0..6)
    if (value == null || !value.isFinite() || value < 0) return null
    var scale = 1.0
    repeat(decimals) { scale *= 10 }
    // At this magnitude representable spacing already exceeds our display precision.
    if (value > Double.MAX_VALUE / scale) return value
    return kotlin.math.ceil(value * scale) / scale
}

fun formatFuelTimeUpperBound(seconds: Double?): String = formatFuelTime(roundUpperBound(seconds, 0))
