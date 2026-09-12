package voidmei.fm

/** Group names follow legacy display routing, not inferred physical surface orientation. */
enum class AerodynamicPartKind(val label: String) {
    CLEAN("无襟翼器件"), FULL("满襟翼器件"), FUSELAGE("机身器件"), FIN("Fin 器件"), STAB("Stab 器件")
}
data class AerodynamicPart(val kind: AerodynamicPartKind, val sourcePath: String, val sweepRatio: Double?,
    val cdMin: Double?, val cl0: Double?, val alphaLow: Double?, val alphaHigh: Double?, val clLow: Double?, val clHigh: Double?)
data class AerodynamicPartsResult(val parts: List<AerodynamicPart>, val issues: List<String>)

object AerodynamicPartsExtractor {
    fun extract(document: BlkBlock): AerodynamicPartsResult {
        data class Candidate(val kind: AerodynamicPartKind, val path: String, val block: BlkBlock, val sweep: Pair<String, BlkBlock>?)
        val candidates = mutableListOf<Candidate>()
        fun visit(block: BlkBlock, path: String, sweep: Pair<String, BlkBlock>?) {
            for (child in block.entries.filterIsInstance<BlkBlock>()) {
                val next = if (path.isEmpty()) child.name else "$path.${child.name}"
                val owner = if (Regex("WingPlaneSweep[0-9]+", RegexOption.IGNORE_CASE).matches(child.name)) next to child else sweep
                val kind = when (child.name.lowercase()) {
                    "noflaps", "flapspolar0" -> AerodynamicPartKind.CLEAN
                    "fullflaps", "flapspolar1" -> AerodynamicPartKind.FULL
                    "fuselage" -> AerodynamicPartKind.FUSELAGE
                    "fin" -> AerodynamicPartKind.FIN
                    "stab" -> AerodynamicPartKind.STAB
                    "polar" -> when (block.name.lowercase()) {
                        "fuselageplane" -> AerodynamicPartKind.FUSELAGE
                        "horstabplane" -> AerodynamicPartKind.FIN
                        "verstabplane" -> AerodynamicPartKind.STAB
                        else -> null
                    }
                    else -> null
                }
                if (kind != null) candidates += Candidate(kind, next, child, owner)
                visit(child, next, owner)
            }
        }
        visit(document, "", null)
        val issues = mutableListOf<String>()
        val parts = candidates.groupBy { it.path.lowercase() }.values.mapNotNull { group ->
            if (group.size != 1) { issues += "气动器件来源重复：${group.first().path}"; return@mapNotNull null }
            val c = group.single()
            fun number(block: BlkBlock, path: String, name: String): Double? {
                val fields = block.entries.filterIsInstance<BlkField>().filter { it.name.equals(name, true) }
                if (fields.isEmpty()) return null
                val field = fields.singleOrNull()
                val value = field?.takeIf { it.type.lowercase() in setOf("r", "i", "i64") }?.number()
                if (value == null) issues += "气动器件字段无效或重复：$path.$name"
                return value
            }
            val sweep = c.sweep?.let { (path, block) -> number(block, path, "Sweep")?.let {
                if (it in 0.0..1.0) it else { issues += "气动器件后掠比例无效：$path.Sweep"; null }
            } }
            AerodynamicPart(c.kind, c.path, sweep, number(c.block, c.path, "CdMin"), number(c.block, c.path, "Cl0"),
                number(c.block, c.path, "alphaCritLow"), number(c.block, c.path, "alphaCritHigh"),
                number(c.block, c.path, "ClCritLow"), number(c.block, c.path, "ClCritHigh"))
        }
        return AerodynamicPartsResult(parts, issues.distinct())
    }
}
