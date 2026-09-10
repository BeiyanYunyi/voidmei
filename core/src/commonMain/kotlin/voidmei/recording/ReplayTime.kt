package voidmei.recording

object ReplayTime {
    fun position(startMs: Long, endMs: Long, anchorMs: Long, elapsedMs: Double, speed: Double, loop: Boolean): Long {
        require(startMs >= 0 && endMs >= startMs && anchorMs in startMs..endMs)
        require(elapsedMs.isFinite() && elapsedMs >= 0 && speed.isFinite() && speed > 0)
        val duration = endMs - startMs
        if (duration == 0L) return startMs
        val advanced = (anchorMs - startMs) + elapsedMs * speed
        require(advanced.isFinite())
        val relative = if (loop) advanced % duration else advanced.coerceAtMost(duration.toDouble())
        return startMs + relative.toLong().coerceIn(0, duration)
    }
}
