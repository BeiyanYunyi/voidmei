package voidmei.fm

data class PowerStageTransition(val fromAltitudeM: Double, val toAltitudeM: Double,
    val fromStage: Int, val toStage: Int)

enum class PowerExtremumKind { PEAK, VALLEY, KINK }
data class PowerExtremum(val point: PistonPowerPoint, val kind: PowerExtremumKind)

/** Summarizes known samples only; gaps never imply a compressor transition. */
data class PowerCurveSummary(val peak: PistonPowerPoint?, val transitions: List<PowerStageTransition>,
    val extrema: List<PowerExtremum> = emptyList()) {
    companion object {
        fun from(points: List<PistonPowerPoint?>): PowerCurveSummary {
            var peak: PistonPowerPoint? = null
            var previous: PistonPowerPoint? = null
            val transitions = mutableListOf<PowerStageTransition>()
            points.forEach { raw ->
                val point = raw?.takeIf { it.altitudeM.isFinite() && it.powerHp.isFinite() && it.powerHp >= 0 && it.stageIndex >= 0 }
                if (point != null && (peak == null || point.powerHp > peak!!.powerHp)) peak = point
                val before = previous
                if (point != null && before != null && point.altitudeM > before.altitudeM && point.stageIndex != before.stageIndex) {
                    transitions += PowerStageTransition(before.altitudeM, point.altitudeM, before.stageIndex, point.stageIndex)
                }
                previous = point
            }
            val extrema = findExtrema(points, peak?.powerHp ?: 0.0)
            return PowerCurveSummary(peak, transitions, (extrema + findKinks(points, extrema)).sortedBy { it.point.altitudeM })
        }

        private fun findKinks(points: List<PistonPowerPoint?>, extrema: List<PowerExtremum>): List<PowerExtremum> {
            val result = mutableListOf<PowerExtremum>()
            val segment = mutableListOf<PistonPowerPoint>()
            fun finishSegment() {
                if (segment.size >= 9) {
                    val average = kotlin.math.abs(segment.last().powerHp - segment.first().powerHp) /
                        (segment.last().altitudeM - segment.first().altitudeM)
                    val threshold = maxOf(average * 2.5, 0.08)
                    fun change(index: Int): Double {
                        val left = (segment[index].powerHp - segment[index - 4].powerHp) / 100
                        val right = (segment[index + 4].powerHp - segment[index].powerHp) / 100
                        return if (left * right >= 0) kotlin.math.abs(right - left) else 0.0
                    }
                    fun nearby(point: PistonPowerPoint) = (extrema + result).any {
                        kotlin.math.abs(it.point.altitudeM - point.altitudeM) < 300
                    }
                    for (index in 4 until segment.size - 4) {
                        if (nearby(segment[index]) || change(index) <= threshold) continue
                        var best = index
                        for (candidate in maxOf(4, index - 2)..minOf(segment.lastIndex - 4, index + 2)) {
                            if (change(candidate) > change(best)) best = candidate
                        }
                        if (!nearby(segment[best])) result += PowerExtremum(segment[best], PowerExtremumKind.KINK)
                    }
                }
                segment.clear()
            }
            for (point in points) {
                if (point == null || !point.altitudeM.isFinite() || !point.powerHp.isFinite() || point.powerHp < 0 || point.stageIndex < 0) {
                    finishSegment()
                    continue
                }
                if (segment.isNotEmpty() && kotlin.math.abs(point.altitudeM - segment.last().altitudeM - 25) > 0.001) finishSegment()
                segment += point
            }
            finishSegment()
            return result
        }

        /** Legacy 25 m sampling: ±100 m neighborhood, 0.5% prominence, 300 m same-kind separation. */
        private fun findExtrema(points: List<PistonPowerPoint?>, maximum: Double): List<PowerExtremum> {
            val result = mutableListOf<PowerExtremum>()
            for (index in 4 until points.size - 4) {
                val window = points.subList(index - 4, index + 5)
                if (window.any { it == null || !it.altitudeM.isFinite() || !it.powerHp.isFinite() || it.powerHp < 0 || it.stageIndex < 0 }) continue
                val known = window.filterNotNull()
                // Never infer across missing samples, reversed altitude, or an irregular grid.
                if (known.zipWithNext().any { (a, b) -> kotlin.math.abs(b.altitudeM - a.altitudeM - 25.0) > 0.001 }) continue
                val left = known.first().powerHp
                val center = known[4].powerHp
                val right = known.last().powerHp
                val kind = when {
                    center > left && center > right && center - minOf(left, right) > maximum * 0.005 -> PowerExtremumKind.PEAK
                    center < left && center < right && maxOf(left, right) - center > maximum * 0.005 -> PowerExtremumKind.VALLEY
                    else -> continue
                }
                val best = if (kind == PowerExtremumKind.PEAK) known.maxBy { it.powerHp } else known.minBy { it.powerHp }
                if (result.none { it.kind == kind && kotlin.math.abs(it.point.altitudeM - best.altitudeM) < 300.0 }) {
                    result += PowerExtremum(best, kind)
                }
            }
            return result.sortedBy { it.point.altitudeM }
        }
    }
}
