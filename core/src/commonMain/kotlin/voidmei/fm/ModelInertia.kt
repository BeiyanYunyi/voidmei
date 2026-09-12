package voidmei.fm

/** Axis mapping follows the Java FM display: vector [roll, yaw, pitch]. */
data class ModelInertia(val pitch: Double, val roll: Double, val yaw: Double, val sourcePath: String) {
    init { require(listOf(pitch, roll, yaw).all { it.isFinite() && it >= 0 }) }
}
data class ModelInertiaResult(val inertia: ModelInertia?, val issues: List<String>)

object ModelInertiaExtractor {
    fun extract(document: BlkBlock): ModelInertiaResult {
        val all = document.fields()
        val matches = all.filter { it.first.equals("MomentOfInertia", true) }.ifEmpty {
            all.filter { it.first.endsWith(".MomentOfInertia", true) }
        }
        if (matches.isEmpty()) return ModelInertiaResult(null, emptyList())
        if (matches.size != 1) return ModelInertiaResult(null, listOf("MomentOfInertia 来源重复或不明确"))
        val (path, field) = matches.single()
        val values = field.values.map { it.toDoubleOrNull()?.takeIf { v -> v.isFinite() && v >= 0 } }
        if (!field.type.equals("p3", true) || values.size != 3 || values.any { it == null })
            return ModelInertiaResult(null, listOf("$path 需要三个有限非负数值的 p3 向量"))
        return ModelInertiaResult(ModelInertia(values[2]!!, values[0]!!, values[1]!!, path), emptyList())
    }
}
