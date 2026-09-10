package voidmei.fm

data class ResolvedEngineInstance(val binding: EngineBinding, val parameters: BlkBlock)
data class EngineInstanceResult(val engines: List<ResolvedEngineInstance>, val issues: List<String>) {
    fun document(): BlkBlock = BlkBlock("", engines.map { it.parameters })
}

/** Explicit instance fields override type defaults; missing fields inherit recursively. */
object EngineInstanceResolver {
    fun resolve(document: BlkBlock): EngineInstanceResult {
        val bindings = EngineBindingExtractor.extract(document)
        val blocks = document.entries.filterIsInstance<BlkBlock>()
        val issues = bindings.issues.toMutableList()
        val engines = bindings.bindings.mapNotNull { binding ->
            try {
                val instance = blocks.single { it.name == binding.instance }
                val source = blocks.single { it.name == binding.parameterSource }
                validate(source, source.name, 0)
                validate(instance, instance.name, 0)
                val effective = if (source === instance) source else merge(source,
                    instance.copy(entries = instance.entries.filterNot { it is BlkField && it.name.equals("Type", true) }), instance.name)
                ResolvedEngineInstance(binding, effective.copy(name = instance.name))
            } catch (e: IllegalArgumentException) {
                issues += "${binding.instance}: ${e.message}"
                null
            }
        }
        return EngineInstanceResult(engines, issues)
    }

    private fun validate(block: BlkBlock, path: String, depth: Int) {
        require(depth <= 64) { "实例参数嵌套过深：$path" }
        val duplicates = block.entries.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "重复实例参数：$path.${duplicates.first()}" }
        block.entries.filterIsInstance<BlkBlock>().forEach { validate(it, "$path.${it.name}", depth + 1) }
    }

    private fun merge(base: BlkBlock, override: BlkBlock, path: String): BlkBlock {
        val remaining = override.entries.associateBy { it.name.lowercase() }.toMutableMap()
        val entries = base.entries.map { original ->
            val replacement = remaining.remove(original.name.lowercase()) ?: return@map original
            when {
                original is BlkBlock && replacement is BlkBlock -> merge(original, replacement, "$path.${original.name}")
                original is BlkField && replacement is BlkField -> {
                    val numeric = setOf("r", "i", "i64")
                    require(original.type.equals(replacement.type, true) ||
                        original.type.lowercase() in numeric && replacement.type.lowercase() in numeric) {
                        "覆盖字段类型冲突：$path.${original.name}"
                    }
                    replacement
                }
                else -> throw IllegalArgumentException("覆盖字段与子块冲突：$path.${original.name}")
            }
        } + remaining.values
        return BlkBlock(override.name, entries)
    }
}
