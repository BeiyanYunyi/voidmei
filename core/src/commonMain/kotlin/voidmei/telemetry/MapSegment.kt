package voidmei.telemetry

/** Liang–Barsky clipping before pixel conversion; overflowing geometry remains unknown. */
data class MapSegment(val start: MapPoint, val end: MapPoint) {
    fun clipped(): MapSegment? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (listOf(start.x, start.y, end.x, end.y, dx, dy).any { !it.isFinite() }) return null
        var enter = 0.0
        var leave = 1.0
        for ((p, q) in listOf(-dx to start.x, dx to 1 - start.x, -dy to start.y, dy to 1 - start.y)) {
            if (p == 0.0) {
                if (q < 0) return null
            } else {
                val t = q / p
                if (p < 0) enter = maxOf(enter, t) else leave = minOf(leave, t)
                if (enter > leave) return null
            }
        }
        fun point(t: Double) = MapPoint((start.x + t * dx).coerceIn(0.0, 1.0),
            (start.y + t * dy).coerceIn(0.0, 1.0))
        return MapSegment(point(enter), point(leave))
    }
}
