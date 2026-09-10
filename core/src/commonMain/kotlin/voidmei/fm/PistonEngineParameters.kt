package voidmei.fm

data class RawCompressorStage(
    val altitudeM: Double, val powerHp: Double,
    val afterburnerBoost: Double?, val curvature: Double?,
    val ceilingM: Double?, val ceilingPowerHp: Double?,
    val constRpmAltitudeM: Double?, val constRpmPowerHp: Double?,
    val afterburnerPressureBoost: Double?,
)

/** Raw values retain absence and explicit zero; adjustments belong to a later pipeline. */
data class PistonEngineParameters(
    val source: String, val type: String, val stages: List<RawCompressorStage>,
    val deckPowerHp: Double?, val afterburnerBoost: Double?, val throttleBoost: Double?,
    val octaneMultiplier: Double?, val shaftRpmMax: Double?, val rpmNominal: Double?,
    val governorMax: Double?, val militaryRpm: Double?, val wepRpm: Double?,
    val speedManifoldMultiplier: Double?, val pressureAtRpmZero: Double?, val omegaFactorSquared: Double?,
    val exactAltitudes: Boolean?, val manifoldPressures: List<Double>, val wepManifoldPressure: Double?,
)

data class PistonParameterResult(val engines: List<PistonEngineParameters>, val issues: List<String>)

object PistonParameterExtractor {
    fun extract(document: BlkBlock): PistonParameterResult {
        val engines = mutableListOf<PistonEngineParameters>()
        val issues = mutableListOf<String>()
        val candidates = document.entries.filterIsInstance<BlkBlock>()
            .filter { Regex("Engine(?:Type)?[0-9]+", RegexOption.IGNORE_CASE).matches(it.name) }
        val duplicates = candidates.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.keys
        for (engine in candidates) {
            if (engine.name.lowercase() in duplicates) {
                issues += "${engine.name}: 重复发动机块，无法确定参数"
                continue
            }
            try { read(engine)?.let(engines::add) }
            catch (e: IllegalArgumentException) { issues += "${engine.name}: ${e.message}" }
        }
        return PistonParameterResult(engines, issues.distinct())
    }

    private fun read(engine: BlkBlock): PistonEngineParameters? {
        fun block(parent: BlkBlock, vararg names: String): BlkBlock? {
            val matches = parent.entries.filterIsInstance<BlkBlock>().filter { b -> names.any { it.equals(b.name, true) } }
            require(matches.size <= 1) { "重复参数块 ${names.joinToString("/")}" }
            return matches.singleOrNull()
        }
        fun field(parent: BlkBlock?, key: String): BlkField? {
            val fields = parent?.entries?.filterIsInstance<BlkField>()?.filter { it.name.equals(key, true) }.orEmpty()
            require(fields.size <= 1) { "重复字段 $key" }
            return fields.singleOrNull()
        }
        fun number(parent: BlkBlock?, key: String): Double? = field(parent, key)?.let {
            require(it.type.lowercase() in listOf("r", "i", "i64")) { "$key 不是数值字段" }
            requireNotNull(it.number()) { "$key 不是有限标量" }
        }
        val main = requireNotNull(block(engine, "Main")) { "缺少 Main" }
        val typeField = requireNotNull(field(main, "Type")) { "缺少 Main.Type" }
        require(typeField.type.equals("t", true)) { "Main.Type 不是文本" }
        val type = requireNotNull(typeField.values.singleOrNull()) { "Main.Type 不是单值" }
        if (type.equals("Jet", true)) return null
        require(type.lowercase() in listOf("inline", "radial", "piston")) { "尚不支持发动机类型 $type" }
        val compressor = requireNotNull(block(engine, "Compressor")) { "缺少 Compressor" }
        val count = requireNotNull(number(compressor, "NumSteps")) { "缺少 NumSteps" }
        require(count in 1.0..32.0 && count == count.toInt().toDouble()) { "NumSteps 必须为 1–32 的整数" }
        val stages = (0 until count.toInt()).map { i ->
            val altitude = requireNotNull(number(compressor, "Altitude$i")) { "缺少 Altitude$i" }
            val power = requireNotNull(number(compressor, "Power$i")) { "缺少 Power$i" }
            require(power > 0) { "Power$i 必须为正值" }
            RawCompressorStage(altitude, power, number(compressor, "AfterburnerBoostMul$i"),
                number(compressor, "PowerConstRPMCurvature$i"), number(compressor, "Ceiling$i"),
                number(compressor, "PowerAtCeiling$i"), number(compressor, "AltitudeConstRPM$i"),
                number(compressor, "PowerConstRPM$i"), number(compressor, "AfterburnerPressureBoost$i"))
        }
        val propeller = block(engine, "Propellor", "Propeller")
        val rpmFields = (main.entries + propeller?.entries.orEmpty()).filterIsInstance<BlkField>().filter {
            Regex("ThrottleRPMAuto[0-9]+", RegexOption.IGNORE_CASE).matches(it.name)
        }
        val rpmPairs = rpmFields.map { f ->
            require(f.type.equals("p2", true) && f.values.size == 2) { "${f.name} 必须为 p2 油门/转速对" }
            val values = f.values.map { requireNotNull(it.toDoubleOrNull()?.takeIf(Double::isFinite)) { "${f.name} 包含无效数字" } }
            require(values[1] > 0) { "${f.name} 转速必须为正值" }
            values[0] to values[1]
        }
        fun rpm(throttle: Double): Double? {
            val values = rpmPairs.filter { kotlin.math.abs(it.first - throttle) < 0.01 }.map { it.second }.distinct()
            require(values.size <= 1) { "油门 $throttle 对应多个转速" }
            return values.singleOrNull()
        }
        val exact = field(compressor, "ExactAltitudes")?.let {
            require(it.type.equals("b", true)) { "ExactAltitudes 不是布尔值" }
            when (it.values.singleOrNull()?.lowercase()) {
                "yes", "true", "1" -> true
                "no", "false", "0" -> false
                else -> throw IllegalArgumentException("ExactAltitudes 包含无效布尔值")
            }
        }
        val pressures = compressor.entries.filterIsInstance<BlkField>().filter {
            Regex("ATA[0-9]+", RegexOption.IGNORE_CASE).matches(it.name)
        }.map { requireNotNull(number(compressor, it.name)) }
        return PistonEngineParameters(engine.name, type, stages,
            number(main, "Power"), number(main, "AfterburnerBoost"), number(main, "ThrottleBoost"),
            number(main, "OctaneAfterburnerMult"), number(main, "ShaftRPMMax"), number(main, "RPMNom"),
            number(propeller, "GovernorMaxParam"), rpm(1.0), rpm(1.1),
            number(compressor, "SpeedManifoldMultiplier"), number(compressor, "CompressorPressureAtRPM0"),
            number(compressor, "CompressorOmegaFactorSq"), exact, pressures,
            // Legacy lookup did not specify this field's section. Accept one occurrence
            // within this engine, and reject ambiguity instead of mixing engine types.
            number(BlkBlock("", engine.fields().map { it.second }.filter {
                it.name.equals("AfterburnerManifoldPressure", true)
            }), "AfterburnerManifoldPressure"))
    }
}
