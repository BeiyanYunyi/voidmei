package voidmei.telemetry

/** Fit map metres into a drawing area with one common scale for both axes. */
data class MapViewport(val left: Double, val top: Double, val width: Double, val height: Double) {
    fun project(point: MapPoint) = MapPoint(left + point.x * width, top + point.y * height)

    companion object {
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
