package voidmei.telemetry

import kotlin.math.hypot
import kotlin.math.abs

object MapHitTest {
    /** Select a uniquely nearest visible point marker; segments and ambiguous overlaps are omitted. */
    fun nearest(snapshot: MapSnapshot, viewport: MapViewport, tap: MapPoint, radius: Double): MapObject? {
        if (!radius.isFinite() || radius < 0 || !tap.x.isFinite() || !tap.y.isFinite() ||
            tap.x !in viewport.left..(viewport.left + viewport.width) ||
            tap.y !in viewport.top..(viewport.top + viewport.height)) return null
        val candidates = snapshot.objects.mapNotNull { obj ->
            val point = obj.position?.takeIf { it.x in 0.0..1.0 && it.y in 0.0..1.0 } ?: return@mapNotNull null
            val screen = viewport.project(point)
            val distance = hypot(screen.x - tap.x, screen.y - tap.y)
            if (distance <= radius) obj to distance else null
        }
        val nearest = candidates.minByOrNull { it.second } ?: return null
        if (candidates.count { abs(it.second - nearest.second) < 1e-6 } != 1) return null
        return nearest.first
    }
}
