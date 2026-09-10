package voidmei.fm

data class EngineThermalBand(val index: Int, val waterTemperatureC: Double?, val oilTemperatureC: Double?,
    val workSeconds: Double?, val recoverSeconds: Double?)
data class EngineThermalParameters(val telemetryIndex: Int, val bands: List<EngineThermalBand>)
data class EngineThermalResult(val engines: List<EngineThermalParameters>, val issues: List<String>)

/** Raw model time budgets, never a claim about the engine's remaining lifetime. */
object EngineThermalExtractor {
    fun extract(instances: EngineInstanceResult): EngineThermalResult {
        val issues = mutableListOf<String>()
        val engines = instances.engines.mapNotNull { engine ->
            try {
                val temperature = engine.parameters.entries.filterIsInstance<BlkBlock>().singleOrNull { it.name.equals("Temperature", true) }
                    ?: return@mapNotNull null
                val pattern = Regex("Load([0-9]+)", RegexOption.IGNORE_CASE)
                val loads = temperature.entries.filterIsInstance<BlkBlock>().mapNotNull { block ->
                    val match = pattern.matchEntire(block.name) ?: return@mapNotNull null
                    val index = requireNotNull(match.groupValues[1].toIntOrNull()) { "无效温度档位编号" }
                    index to block
                }.sortedBy { it.first }
                if (loads.isEmpty()) return@mapNotNull null
                require(loads.size <= 64 && loads.map { it.first }.distinct().size == loads.size) { "温度档位过多或编号重复" }
                val bands = loads.map { (index, block) ->
                    fun number(name: String, minimum: Double): Double? {
                        val field = block.entries.filterIsInstance<BlkField>().singleOrNull { it.name.equals(name, true) } ?: return null
                        val value = field.takeIf { it.type.lowercase() in listOf("r", "i", "i64") }?.number()
                        require(value != null && value >= minimum) { "${block.name}.$name 无效" }
                        return value
                    }
                    val water = number("WaterTemperature", -273.15)
                    val oil = number("OilTemperature", -273.15)
                    require(water != null || oil != null) { "${block.name} 缺少温度阈值" }
                    EngineThermalBand(index, water, oil, number("WorkTime", 0.0), number("RecoverTime", 0.0))
                }
                for (channel in listOf<(EngineThermalBand) -> Double?>({ it.waterTemperatureC }, { it.oilTemperatureC })) {
                    require(bands.mapNotNull(channel).zipWithNext().all { (a, b) -> a <= b }) { "温度阈值未按档位递增" }
                }
                EngineThermalParameters(engine.binding.telemetryIndex, bands)
            } catch (e: IllegalArgumentException) {
                issues += "${engine.binding.instance}.Temperature: ${e.message}"
                null
            }
        }
        return EngineThermalResult(engines, issues)
    }
}
