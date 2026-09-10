package voidmei.fm

data class FlapLimitPoint(val ratio: Double, val speedKmh: Double)

/** Verified nodes only; no synthetic 125% flap setting or extrapolated safety limit. */
data class FlapLimits(val points: List<FlapLimitPoint>) {
    init {
        require(points.isNotEmpty() && points.size <= 64)
        require(points.all { it.ratio.isFinite() && it.ratio in 0.0..1.0 && it.speedKmh.isFinite() && it.speedKmh > 0 })
        require(points.zipWithNext().all { (a, b) -> a.ratio < b.ratio && a.speedKmh >= b.speedKmh })
    }
    fun speedAt(percent: Double?): Double? {
        if (percent == null || !percent.isFinite() || percent <= 0 || percent > 100) return null
        val ratio = percent / 100
        if (ratio < points.first().ratio || ratio > points.last().ratio) return null
        points.firstOrNull { it.ratio == ratio }?.let { return it.speedKmh }
        val right = points.indexOfFirst { it.ratio > ratio }
        val a = points[right - 1]; val b = points[right]
        return a.speedKmh + (b.speedKmh - a.speedKmh) * ((ratio - a.ratio) / (b.ratio - a.ratio))
    }
    /** A saturated maximum position below the last node's speed is not a near-limit condition. */
    fun isNearLimit(percent: Double?, iasKmh: Double?, marginPercent: Double): Boolean {
        require(marginPercent.isFinite() && marginPercent >= 0)
        if (percent == null || !percent.isFinite() || percent <= 0 || percent > points.last().ratio * 100 ||
            iasKmh == null || !iasKmh.isFinite() || iasKmh < points.last().speedKmh) return false
        val maximum = maximumPercentAt(iasKmh) ?: return false
        return maximum - percent < marginPercent
    }

    fun maximumPercentAt(iasKmh: Double?): Double? {
        if (iasKmh == null || !iasKmh.isFinite() || iasKmh < 0 || iasKmh > points.first().speedKmh) return null
        if (iasKmh <= points.last().speedKmh) return points.last().ratio * 100
        val right = points.indexOfFirst { it.speedKmh < iasKmh }
        val a = points[right - 1]; val b = points[right]
        return (a.ratio + (b.ratio - a.ratio) * ((a.speedKmh - iasKmh) / (a.speedKmh - b.speedKmh))) * 100
    }
}

data class FlapLimitResult(val limits: FlapLimits?, val issues: List<String>)

object FlapLimitExtractor {
    fun extract(document: BlkBlock): FlapLimitResult {
        val pattern = Regex("FlapsDestructionIndSpeed(?:P[0-9]*)?", RegexOption.IGNORE_CASE)
        val matches = document.fields().filter { pattern.matches(it.first.substringAfterLast('.')) }
        if (matches.isEmpty()) return FlapLimitResult(null, emptyList())
        return try {
            val fields = matches.filter { '.' !in it.first }.ifEmpty { matches }
            require(fields.map { it.first.substringBeforeLast('.', "") }.distinct().size == 1) { "襟翼限制来自多个参数块" }
            require(fields.map { it.first.lowercase() }.distinct().size == fields.size) { "襟翼限制字段重复" }
            fun values(field: BlkField, type: String, size: Int): List<Double> {
                require(field.type.equals(type, true) && field.values.size == size) { "襟翼限制类型或维度无效" }
                return field.values.map { requireNotNull(it.toDoubleOrNull()?.takeIf(Double::isFinite)) { "襟翼限制不是有限数值" } }
            }
            val indexed = fields.filter { it.first.substringAfterLast('.').matches(Regex("FlapsDestructionIndSpeedP[0-9]+", RegexOption.IGNORE_CASE)) }
            val points = if (indexed.isNotEmpty()) {
                require(indexed.size <= 64) { "襟翼档位超过 64 个" }
                indexed.map { (_, field) -> values(field, "p2", 2).let { FlapLimitPoint(it[0], it[1]) } }
            } else {
                val pairs = fields.firstOrNull { it.first.substringAfterLast('.').equals("FlapsDestructionIndSpeedP", true) }
                if (pairs != null) values(pairs.second, "p4", 4).chunked(2).map { FlapLimitPoint(it[0], it[1]) }
                else {
                    val scalar = fields.single().second
                    require(scalar.type.lowercase() in listOf("r", "i", "i64")) { "襟翼速度限制不是数值" }
                    listOf(FlapLimitPoint(1.0, requireNotNull(scalar.number())))
                }
            }
            FlapLimitResult(FlapLimits(points.sortedBy { it.ratio }), emptyList())
        } catch (e: IllegalArgumentException) { FlapLimitResult(null, listOf("襟翼限制无效：${e.message}")) }
    }
}
