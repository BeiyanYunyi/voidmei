package voidmei.fm

data class EngineRpmLimit(val telemetryIndex: Int, val maximumRpm: Double)
data class EngineRpmReference(val telemetryIndex: Int, val nominalRpm: Double, val type: String)
data class EngineRpmLimitResult(val limits: List<EngineRpmLimit>, val issues: List<String>, val references: List<EngineRpmReference> = emptyList())

object EngineRpmLimitExtractor {
    fun extract(instances: EngineInstanceResult): EngineRpmLimitResult {
        val issues = instances.issues.toMutableList()
        val limits = mutableListOf<EngineRpmLimit>()
        val references = mutableListOf<EngineRpmReference>()
        instances.engines.forEach { engine ->
            fun number(name: String): Double? {
                val field = engine.parameters.fields().singleOrNull { it.first.equals("Main.$name", true) }?.second ?: return null
                val rpm = field.takeIf { it.type.lowercase() in listOf("r", "i", "i64") }?.number()?.takeIf { it > 0 }
                if (rpm == null) issues += "${engine.binding.instance}.Main.$name: 无效转速参数"
                return rpm
            }
            number("RPMMaxAllowed")?.let { limits += EngineRpmLimit(engine.binding.telemetryIndex, it) }
            number("RPMMax")?.let { references += EngineRpmReference(engine.binding.telemetryIndex, it, engine.binding.type) }
        }
        return EngineRpmLimitResult(limits, issues, references)
    }
}
