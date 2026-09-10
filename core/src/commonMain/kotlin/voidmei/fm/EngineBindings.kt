package voidmei.fm

data class EngineBinding(val telemetryIndex: Int, val instance: String, val parameterSource: String, val type: String, val hasInstanceOverrides: Boolean = false)
data class EngineBindingResult(val bindings: List<EngineBinding>, val issues: List<String>)

/** EngineN is zero-based in FM; the corresponding telemetry suffix is N+1. */
object EngineBindingExtractor {
    fun extract(document: BlkBlock): EngineBindingResult {
        val blocks = document.entries.filterIsInstance<BlkBlock>()
        val issues = mutableListOf<String>()
        fun indexed(prefix: String): Map<Int, List<BlkBlock>> {
            val regex = Regex("$prefix([0-9]+)", RegexOption.IGNORE_CASE)
            return blocks.mapNotNull { block ->
                val match = regex.matchEntire(block.name) ?: return@mapNotNull null
                val index = match.groupValues[1].toIntOrNull()?.takeIf { it < Int.MAX_VALUE }
                if (index == null) { issues += "${block.name}: 无效发动机编号"; null } else index to block
            }.groupBy({ it.first }, { it.second })
        }
        val instances = indexed("Engine")
        val types = indexed("EngineType")
        fun uniqueBlock(parent: BlkBlock, name: String): BlkBlock? {
            val matches = parent.entries.filterIsInstance<BlkBlock>().filter { it.name.equals(name, true) }
            require(matches.size <= 1) { "重复 $name 参数块" }
            return matches.singleOrNull()
        }
        fun uniqueField(parent: BlkBlock, name: String): BlkField? {
            val matches = parent.entries.filterIsInstance<BlkField>().filter { it.name.equals(name, true) }
            require(matches.size <= 1) { "重复 $name 字段" }
            return matches.singleOrNull()
        }
        val bindings = instances.entries.sortedBy { it.key }.mapNotNull { (index, matches) ->
            try {
                require(matches.size == 1) { "重复实例编号 $index" }
                val instance = matches.single()
                val reference = uniqueField(instance, "Type")
                val inline = uniqueBlock(instance, "Main")
                require(reference == null || inline?.let { uniqueField(it, "Type") } == null) { "类型引用与实例 Main.Type 同时存在" }
                val parameters = if (reference != null) {
                    require(reference.type.lowercase() in listOf("i", "i64")) { "Type 必须为整数引用" }
                    val target = reference.values.singleOrNull()?.toIntOrNull()?.takeIf { it >= 0 }
                    requireNotNull(target) { "无效 Type 引用" }
                    requireNotNull(types[target]?.singleOrNull()) { "EngineType$target 缺失或重复" }
                } else instance
                val main = requireNotNull(uniqueBlock(parameters, "Main")) { "缺少 Main 参数块" }
                val type = requireNotNull(uniqueField(main, "Type")) { "缺少 Main.Type" }
                require(type.type.equals("t", true) && type.values.size == 1 && type.values.single().isNotBlank()) { "Main.Type 必须为非空文本" }
                EngineBinding(index + 1, instance.name, parameters.name, type.values.single(),
                    reference != null && instance.entries.any { it is BlkBlock })
            } catch (e: IllegalArgumentException) {
                issues += "Engine$index: ${e.message}"
                null
            }
        }
        if (types.isNotEmpty() && instances.isEmpty()) issues += "存在发动机类型，但缺少 EngineN 实例，无法对应遥测编号"
        return EngineBindingResult(bindings, issues)
    }
}
