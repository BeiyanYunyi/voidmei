package voidmei.fm

/** Per-engine thrust in kgf on the FM altitude/velocity grid. Missing cells remain unknown. */
data class JetThrustModel(val source: String, val altitudesM: List<Double>, val velocitiesKmh: List<Double>,
    val militaryKgf: List<List<Double?>>, val afterburnerKgf: List<List<Double?>>?) {
    init {
        for (axis in listOf(altitudesM, velocitiesKmh)) {
            require(axis.isNotEmpty() && axis.size <= 128 && axis.all { it.isFinite() })
            require(axis.zipWithNext().all { (a, b) -> b > a })
        }
        require(velocitiesKmh.first() >= 0)
        for (table in listOfNotNull(militaryKgf, afterburnerKgf)) {
            require(table.size == altitudesM.size && table.all { it.size == velocitiesKmh.size })
            require(table.flatten().all { it == null || (it.isFinite() && it >= 0) })
        }
    }

    /** Peak of a complete FM grid; an unknown cell could exceed every known sample. */
    fun peakThrust(afterburner: Boolean = false): Double? {
        val table = if (afterburner) afterburnerKgf ?: return null else militaryKgf
        var peak = 0.0
        for (row in table) for (cell in row) peak = maxOf(peak, cell ?: return null)
        return peak.takeIf { it > 0 }
    }

    /** Bilinear estimate inside the grid only; no extrapolation or filling missing cells. */
    fun thrust(altitudeM: Double, speedKmh: Double, afterburner: Boolean = false): Double? {
        val table = if (afterburner) afterburnerKgf ?: return null else militaryKgf
        fun bracket(axis: List<Double>, value: Double): Triple<Int, Int, Double>? {
            if (!value.isFinite() || value < axis.first() || value > axis.last()) return null
            val index = axis.binarySearch(value)
            if (index >= 0) return Triple(index, index, 0.0)
            val upper = -index - 1
            return Triple(upper - 1, upper, (value - axis[upper - 1]) / (axis[upper] - axis[upper - 1]))
        }
        val (a, b, h) = bracket(altitudesM, altitudeM) ?: return null
        val (c, d, v) = bracket(velocitiesKmh, speedKmh) ?: return null
        var result = 0.0
        for ((row, rw) in listOf(a to 1 - h, b to h)) {
            for ((column, cw) in listOf(c to 1 - v, d to v)) {
                val weight = rw * cw
                if (weight == 0.0) continue
                result += (table[row][column] ?: return null) * weight
            }
        }
        return result.takeIf { it.isFinite() && it >= 0 }
    }
}

data class JetThrustResult(val engines: List<JetThrustModel>, val issues: List<String>)

object JetThrustExtractor {
    fun extract(document: BlkBlock): JetThrustResult {
        val engines = mutableListOf<JetThrustModel>()
        val issues = mutableListOf<String>()
        val blocks = document.entries.filterIsInstance<BlkBlock>().filter {
            Regex("Engine(?:Type)?[0-9]+", RegexOption.IGNORE_CASE).matches(it.name)
        }
        for ((name, matches) in blocks.groupBy { it.name.lowercase() }) {
            try {
                require(matches.size == 1) { "重复发动机块" }
                val engine = matches.single()
                fun block(parent: BlkBlock, key: String): BlkBlock? {
                    val entries = parent.entries.filterIsInstance<BlkBlock>().filter { it.name.equals(key, true) }
                    require(entries.size <= 1) { "重复 $key 块" }
                    return entries.singleOrNull()
                }
                fun field(parent: BlkBlock, key: String): BlkField? {
                    val entries = parent.entries.filterIsInstance<BlkField>().filter { it.name.equals(key, true) }
                    require(entries.size <= 1) { "重复 $key 字段" }
                    return entries.singleOrNull()
                }
                fun number(parent: BlkBlock, key: String): Double? = field(parent, key)?.let {
                    require(it.type.lowercase() in setOf("r", "i", "i64")) { "$key 不是数值" }
                    requireNotNull(it.number()) { "$key 不是有限标量" }
                }
                val main = block(engine, "Main") ?: continue
                val type = field(main, "Type") ?: continue
                if (!type.type.equals("t", true) || !type.values.singleOrNull().equals("Jet", true)) continue
                fun thrustBlocks(parent: BlkBlock): List<BlkBlock> = parent.entries.filterIsInstance<BlkBlock>().flatMap {
                    if (it.name.equals("ThrustMax", true)) listOf(it) else thrustBlocks(it)
                }
                val tables = thrustBlocks(engine)
                require(tables.size == 1) { "缺少或重复 ThrustMax" }
                val thrust = tables.single()
                // Callers supply TAS. Legacy files without a label retain that convention;
                // an explicitly different axis must not silently be interpreted as TAS.
                field(thrust, "VelocityType")?.let {
                    require(it.type.equals("t", true) && it.values.singleOrNull().equals("TAS", true)) {
                        "ThrustMax.VelocityType 尚不支持：${it.values.joinToString()}（需要 TAS）"
                    }
                }
                val base = requireNotNull(number(thrust, "ThrustMax0")) { "缺少 ThrustMax0" }
                require(base > 0) { "ThrustMax0 必须为正值" }
                fun axis(prefix: String): List<Double> {
                    val names = thrust.entries.filterIsInstance<BlkField>().mapNotNull {
                        Regex("${prefix}_([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
                    }.sorted()
                    require(names.isNotEmpty() && names.size <= 128 && names == names.indices.toList()) { "$prefix 编号必须从 0 连续且不超过 128 项" }
                    return names.map { requireNotNull(number(thrust, "${prefix}_$it")) }
                }
                val altitudes = axis("Altitude")
                val velocities = axis("Velocity")
                val boost = number(main, "AfterburnerBoost")
                require(boost == null || boost > 0) { "AfterburnerBoost 必须为正值" }
                val modes = main.entries.filterIsInstance<BlkBlock>().filter { Regex("Mode[0-9]+", RegexOption.IGNORE_CASE).matches(it.name) }
                val modeIndices = modes.map { it.name.drop(4).toInt() }
                require(modeIndices.distinct().size == modes.size) { "重复 Mode 块" }
                val lastMode = modes.maxByOrNull { it.name.drop(4).toInt() }
                val modeMultiplier = lastMode?.let { requireNotNull(number(it, "ThrustMult")) { "末级模式缺少 ThrustMult" } } ?: 1.0
                require(modeMultiplier > 0) { "ThrustMult 必须为正值" }
                val military = altitudes.indices.map { a -> velocities.indices.map { v ->
                    number(thrust, "ThrustMaxCoeff_${a}_$v")?.let { coefficient ->
                        require(coefficient >= 0) { "推力系数不能为负" }
                        base * coefficient
                    }
                } }
                val afterburner = boost?.let { b -> altitudes.indices.map { a -> velocities.indices.map { v ->
                    val coefficient = number(thrust, "ThrAftMaxCoeff_${a}_$v") ?: 1.0
                    require(coefficient >= 0) { "加力系数不能为负" }
                    military[a][v]?.times(b)?.times(coefficient)?.times(modeMultiplier)
                } } }
                engines += JetThrustModel(engine.name, altitudes, velocities, military, afterburner)
            } catch (e: IllegalArgumentException) { issues += "$name: ${e.message}" }
        }
        return JetThrustResult(engines, issues)
    }
}
