package voidmei.fm

data class WingGeometry(val sourcePath: String, val sweepRatio: Double?, val wingArea: Double?, val span: Double?,
    val sweptAngle: Double?, val efficiency: Double?, val efficiencySource: String?, val bodyArea: Double?, val bodySource: String?) {
    val aspectRatio: Double? get() {
        val area = wingArea?.takeIf { it.isFinite() && it > 0 } ?: return null
        val length = span?.takeIf { it.isFinite() && it > 0 } ?: return null
        return (length * length / area).takeIf { it.isFinite() && it > 0 }
    }
}
data class AerodynamicGeometryResult(val wings: List<WingGeometry>, val issues: List<String>)

object AerodynamicGeometryExtractor {
    fun extract(document: BlkBlock): AerodynamicGeometryResult {
        val fields = document.fields()
        val issues = mutableListOf<String>()
        val blocks = mutableListOf<Pair<String, BlkBlock>>()
        fun visit(block: BlkBlock, path: String) {
            block.entries.filterIsInstance<BlkBlock>().forEach { child ->
                val next = if (path.isEmpty()) child.name else "$path.${child.name}"
                if (child.name.equals("WingPlane", true) || Regex("WingPlaneSweep[0-9]+", RegexOption.IGNORE_CASE).matches(child.name)) blocks += next to child
                visit(child, next)
            }
        }
        visit(document, "")
        fun value(path: String, suffix: Boolean = false, nonnegative: Boolean = true): Pair<Double?, String?> {
            val exact = fields.filter { it.first.equals(path, true) }
            val matches = if (suffix) exact.ifEmpty { fields.filter { field ->
                field.first.endsWith(".$path", true) && blocks.none { (wingPath, _) -> field.first.startsWith("$wingPath.", true) }
            } } else exact
            if (matches.isEmpty()) return null to null
            val field = matches.singleOrNull()?.second
            val number = field?.takeIf { it.type.lowercase() in setOf("r", "i", "i64") }?.number()?.takeIf { !nonnegative || it >= 0 }
            if (number == null) issues += "气动几何字段无效或重复：$path"
            return number to (matches.singleOrNull()?.first ?: path)
        }
        fun first(vararg paths: String): Pair<Double?, String?> {
            for (path in paths) {
                val result = value(path, suffix = true)
                if (result.second != null) return result
            }
            return null to null
        }
        val body = first("Areas.Fuselage", "FuselagePlane.Areas.Main")
        val efficiency = value("OswaldsEfficiencyNumber", suffix = true)
        val roots = blocks.groupBy { it.first.lowercase() }.values.mapNotNull { group ->
            if (group.size != 1) { issues += "机翼几何来源重复：${group.first().first}"; null } else group.single().first
        }.toMutableList()
        if (fields.any { it.first.startsWith("Areas.Wing", true) || it.first.equals("Areas.Aileron", true) } ||
            fields.any { it.first.equals("Wingspan", true) || it.first.equals("SweptWingAngle", true) }) roots.add(0, "")
        val segments = listOf("LeftIn", "LeftMid", "LeftOut", "LeftCut", "RightIn", "RightMid", "RightOut", "RightCut", "Aileron")
        val wings = roots.map { root ->
            fun path(name: String) = if (root.isEmpty()) name else "$root.$name"
            val pieces = segments.map { segment -> value(path("Areas." + if (root.isEmpty() && segment != "Aileron") "Wing$segment" else segment)).first }
            val area = if (pieces.any { it == null }) null else pieces.sumOf { it!! }.takeIf { it.isFinite() }
            if (area == null && pieces.any { it != null }) issues += "机翼面积分量不完整或总和无效：${root.ifEmpty { "旧根字段" }}"
            val localEfficiency = if (root.isNotEmpty()) value(path("OswaldsEfficiencyNumber")) else null to null
            val selectedEfficiency = if (localEfficiency.second != null) localEfficiency else efficiency
            val sweep = if (root.substringAfterLast('.').startsWith("WingPlaneSweep", true)) value(path("Sweep")).first?.let {
                if (it <= 1) it else { issues += "机翼后掠比例无效：$root.Sweep"; null }
            } else null
            WingGeometry(root.ifEmpty { "旧根字段" }, sweep, area,
                value(if (root.isEmpty()) "Wingspan" else path("Span")).first,
                value(if (root.isEmpty()) "SweptWingAngle" else path("SweptAngle"), nonnegative = false).first,
                selectedEfficiency.first, selectedEfficiency.second, body.first, body.second)
        }
        return AerodynamicGeometryResult(wings, issues.distinct())
    }
}

fun StallLiftProfile.areaPerTonne(massKg: Double?, fullFlaps: Boolean): Double? {
    val mass = massKg?.takeIf { it.isFinite() && it > 0 } ?: return null
    val area = (if (fullFlaps) fullFlapArea else cleanArea).takeIf { it.isFinite() && it > 0 } ?: return null
    return (area / mass * 1000).takeIf { it.isFinite() && it > 0 }
}
