package voidmei.telemetry

/** Consume arrival edges even when muted; do not replay a greeting when enabled mid-flight. */
class FlightArrivalCue {
    private var flying = false
    private var aircraft: String? = null
    private var lastAttemptMs: Long? = null

    fun update(state: ConnectionState, timeMs: Long, enabled: Boolean): Boolean {
        // A slow in-flight request does not establish a new flight or connection.
        if (state == ConnectionState.Delayed) return false
        val current = state as? ConnectionState.Flying
        val arrived = current != null && (!flying || aircraft != current.telemetry.aircraft)
        flying = current != null
        aircraft = current?.telemetry?.aircraft
        if (!arrived || !enabled) return false
        val previous = lastAttemptMs
        if (previous != null && timeMs >= previous && timeMs - previous in 0 until 10000L) return false
        lastAttemptMs = timeMs
        return true
    }
}
