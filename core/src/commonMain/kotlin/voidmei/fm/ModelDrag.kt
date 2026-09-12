package voidmei.fm

import kotlin.math.PI

data class RadiatorDrag(val sourcePath: String, val coefficient: Double?)
data class RadiatorDragResult(val fields: List<RadiatorDrag>, val issues: List<String>)

object RadiatorDragExtractor {
    fun extract(document: BlkBlock): RadiatorDragResult {
        val issues = mutableListOf<String>()
        val fields = document.fields().filter { (path, _) ->
            path.substringAfterLast('.').lowercase() in setOf("radiatorcd", "oilradiatorcd")
        }.groupBy { it.first.lowercase() }.values.map { group ->
            val field = group.singleOrNull()?.second
            val number = field?.takeIf { it.type.lowercase() in setOf("r", "i", "i64") }?.number()
            if (number == null) issues += "散热器阻力字段无效或重复：${group.first().first}"
            RadiatorDrag(group.first().first, number)
        }
        return RadiatorDragResult(fields, issues)
    }
}

data class ModelDragReference(
    val geometrySource: String,
    val cleanSource: String?,
    val bodySource: String?,
    val dragArea: Double?,
    val inducedFactor: Double?,
    val halfFuelMassKg: Double?,
) {
    val areaPerTonne: Double? get() = if (dragArea != null && halfFuelMassKg != null)
        (dragArea / halfFuelMassKg * 1000).takeIf { it.isFinite() } else null
    val massTimesInducedFactor: Double? get() = if (inducedFactor != null && halfFuelMassKg != null)
        (halfFuelMassKg * inducedFactor).takeIf { it.isFinite() } else null
}

/** Static legacy drag references. No inferred polar alias priority or cross-wing fallback. */
fun FlightModelParameters.dragReferences(): List<ModelDragReference> {
    val mass = basicMassKg?.takeIf { it.isFinite() && it > 0 }?.let { base ->
        maximumFuelMassKg?.takeIf { it.isFinite() && it >= 0 }?.let { fuel ->
            (base + fuel / 2).takeIf { it.isFinite() && it > 0 }
        }
    }
    fun part(kind: AerodynamicPartKind, paths: List<String>) = aerodynamicParts.filter { candidate ->
        candidate.kind == kind && paths.any { it.equals(candidate.sourcePath, true) }
    }
    return aerodynamicGeometry.map { wing ->
        val root = if (wing.sourcePath == "旧根字段") "" else wing.sourcePath
        fun under(prefix: String, name: String) = if (prefix.isEmpty()) name else "$prefix.$name"
        val localPaths = listOf(under(root, "NoFlaps"), under(root, "FlapsPolar0"))
        val local = part(AerodynamicPartKind.CLEAN, localPaths)
        val hasLocalSource = aerodynamicPartSourcePaths.any { source -> localPaths.any { it.equals(source, true) } }
        // Fixed-wing models may keep the polar beside WingPlane. Swept profiles require their own polar.
        val cleanPaths = if (!hasLocalSource && local.isEmpty() && root.substringAfterLast('.').equals("WingPlane", true)) {
            val parent = root.substringBeforeLast('.', "")
            listOf(under(parent, "NoFlaps"), under(parent, "FlapsPolar0"))
        } else localPaths
        val sourceCount = aerodynamicPartSourcePaths.count { source -> cleanPaths.any { it.equals(source, true) } }
        val clean = if (sourceCount > 1) null else part(AerodynamicPartKind.CLEAN, cleanPaths).singleOrNull()
        val bodyPaths = wing.bodySource?.let { source ->
            when {
                source.endsWith("FuselagePlane.Areas.Main", true) -> listOf(source.dropLast("Areas.Main".length) + "Polar")
                source.endsWith("Areas.Fuselage", true) -> listOf(source.dropLast("Areas.Fuselage".length) + "Fuselage")
                else -> emptyList()
            }
        }.orEmpty()
        val body = part(AerodynamicPartKind.FUSELAGE, bodyPaths).singleOrNull()
        fun Double?.nonnegative() = this?.takeIf { it.isFinite() && it >= 0 }
        val area = wing.wingArea.nonnegative()?.let { wingArea -> clean?.cdMin.nonnegative()?.let { wingCd ->
            wing.bodyArea.nonnegative()?.let { bodyArea -> body?.cdMin.nonnegative()?.let { bodyCd ->
                (wingArea * wingCd + bodyArea * bodyCd).takeIf { it.isFinite() }
            } }
        } }
        val induced = wing.aspectRatio?.let { ar -> wing.efficiency?.takeIf { it.isFinite() && it > 0 }?.let { e ->
            (1.0 / PI / ar / e).takeIf { it.isFinite() && it > 0 }
        } }
        ModelDragReference(wing.sourcePath, clean?.sourcePath, body?.sourcePath, area, induced, mass)
    }
}
