package voidmei.fm

data class FuelModification(val id: String, val addedHorsepower: Double?, val afterburnerMultiplier: Double?,
    val compressorMultiplier: Double?, val inverted: Boolean)
data class FuelModificationResult(val options: List<FuelModification>, val issues: List<String>)

object FuelModificationExtractor {
    private val known = setOf("ussr_fuel_b-95", "ussr_fuel_b-100", "150_octan_fuel", "100_octan_spitfire")

    fun extract(central: BlkBlock): FuelModificationResult {
        val groups = central.entries.filterIsInstance<BlkBlock>().filter { it.name.equals("modifications", true) }
        if (groups.isEmpty()) return FuelModificationResult(emptyList(), emptyList())
        if (groups.size > 1) return FuelModificationResult(emptyList(), listOf("重复 modifications 块"))
        val blocks = groups.single().entries.filterIsInstance<BlkBlock>().filter { it.name.lowercase() in known }
        val options = mutableListOf<FuelModification>()
        val issues = mutableListOf<String>()
        for ((id, matches) in blocks.groupBy { it.name.lowercase() }) {
            try {
                require(matches.size == 1) { "重复燃油改装块" }
                val mod = matches.single()
                val effects = mod.entries.filterIsInstance<BlkBlock>().filter { it.name.equals("effects", true) }
                require(effects.size == 1) { "缺少或重复 effects 块" }
                fun field(block: BlkBlock, name: String): BlkField? {
                    val found = block.entries.filterIsInstance<BlkField>().filter { it.name.equals(name, true) }
                    require(found.size <= 1) { "重复字段 $name" }
                    return found.singleOrNull()
                }
                fun number(name: String): Double? = field(effects.single(), name)?.let {
                    require(it.type.lowercase() in setOf("r", "i", "i64")) { "$name 不是数值" }
                    requireNotNull(it.number()) { "$name 不是有限数值" }
                }
                val inverted = field(mod, "invertEnableLogic")?.let {
                    require(it.type.equals("b", true)) { "invertEnableLogic 不是布尔值" }
                    when (it.values.singleOrNull()?.lowercase()) {
                        "yes", "true", "1" -> true
                        "no", "false", "0" -> false
                        else -> throw IllegalArgumentException("无效 invertEnableLogic")
                    }
                } ?: false
                val hp = number("addHorsePowers")
                val afterburner = number("afterburnerMult")
                val compressor = number("afterburnerCompressorMult")
                require(hp == null || hp >= 0) { "addHorsePowers 不能为负" }
                require(afterburner == null || afterburner > 0) { "afterburnerMult 必须为正" }
                require(compressor == null || compressor > 0) { "afterburnerCompressorMult 必须为正" }
                if (id.startsWith("ussr_")) require(hp != null) { "缺少 addHorsePowers" }
                options += FuelModification(id, hp, afterburner, compressor, inverted)
            } catch (e: IllegalArgumentException) { issues += "$id: ${e.message}" }
        }
        return FuelModificationResult(options, issues)
    }
}
