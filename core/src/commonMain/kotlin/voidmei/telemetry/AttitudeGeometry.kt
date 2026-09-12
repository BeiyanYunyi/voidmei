package voidmei.telemetry

import kotlin.math.*

data class AttitudeGeometry(val pitchDeg: Double, val rollDeg: Double) {
    /** Screen-relative projection: positive pitch moves the horizon down, positive roll rotates it counterclockwise. */
    fun project(horizontal: Double, ladderPitchDeg: Double, pixelsPerDegree: Double): Pair<Double, Double> {
        val angle = rollDeg * PI / 180
        val vertical = (pitchDeg - ladderPitchDeg) * pixelsPerDegree
        return (horizontal * cos(angle) + vertical * sin(angle)) to
            (-horizontal * sin(angle) + vertical * cos(angle))
    }
    companion object {
        /** North relative to an aircraft-heading-up display; screen Y grows downwards. */
        fun northDirection(degrees: Double?): Pair<Double, Double>? = heading(degrees)?.let {
            val angle = it * PI / 180
            -sin(angle) to -cos(angle)
        }
        /** War Thunder reports negative aviahorizon_pitch for nose-up; display pitch is nose-up positive. */
        fun fromIndicators(pitch: Double?, roll: Double?): AttitudeGeometry? {
            if (pitch == null || roll == null || !pitch.isFinite() || !roll.isFinite() || pitch !in -90.0..90.0) return null
            return AttitudeGeometry(if (pitch == 0.0) 0.0 else -pitch, ((roll + 180) % 360 + 360) % 360 - 180)
        }
        fun heading(degrees: Double?): Double? = degrees?.takeIf { it.isFinite() }?.let { ((it % 360) + 360) % 360 }
    }
}
