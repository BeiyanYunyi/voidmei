package voidmei.telemetry

/** Fit map metres into a drawing area with one common scale for both axes. */
data class MapViewport(val left: Double, val top: Double, val width: Double, val height: Double) {
    fun project(point: MapPoint) = MapPoint(left + point.x * width, top + point.y * height)

    companion object {
        /** Fit drawable geometry with 8% screen margins and a 20x zoom limit. */
        fun fitObjects(snapshot: MapSnapshot, availableWidth: Double, availableHeight: Double): MapViewport? {
            val full = fit(snapshot.bounds, availableWidth, availableHeight) ?: return null
            val points = buildList {
                snapshot.objects.forEach { obj ->
                    obj.position?.takeIf { it.x in 0.0..1.0 && it.y in 0.0..1.0 }?.let { add(it) }
                    val start = obj.start
                    val end = obj.end
                    if (start != null && end != null) MapSegment(start, end).clipped()?.let {
                        add(it.start)
                        add(it.end)
                    }
                }
            }
            if (points.isEmpty()) return full
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val zoom = minOf(
                availableWidth * 0.84 / ((maxX - minX) * full.width),
                availableHeight * 0.84 / ((maxY - minY) * full.height),
                20.0,
            )
            val width = full.width * zoom
            val height = full.height * zoom
            return MapViewport(availableWidth / 2 - (minX + maxX) / 2 * width,
                availableHeight / 2 - (minY + maxY) / 2 * height, width, height)
        }

        fun fit(bounds: MapBounds, availableWidth: Double, availableHeight: Double): MapViewport? {
            val w = bounds.widthM
            val h = bounds.heightM
            if (listOf(w, h, availableWidth, availableHeight).any { !it.isFinite() || it <= 0 }) return null
            val scale = minOf(availableWidth / w, availableHeight / h)
            val width = w * scale
            val height = h * scale
            if (listOf(scale, width, height).any { !it.isFinite() || it <= 0 }) return null
            return MapViewport((availableWidth - width) / 2, (availableHeight - height) / 2, width, height)
        }
    }
}
