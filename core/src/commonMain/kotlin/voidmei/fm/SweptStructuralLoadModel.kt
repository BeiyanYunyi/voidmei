package voidmei.fm

data class StructuralLoadProfile(val sweep: Double, val model: StructuralLoadModel)

/** Linear estimate between FM sweep profiles, with the same endpoint policy as wing limits. */
data class SweptStructuralLoadModel(val profiles: List<StructuralLoadProfile>) {
    init {
        require(profiles.isNotEmpty())
        require(profiles.all { it.sweep.isFinite() && it.sweep in 0.0..1.0 })
        require(profiles.zipWithNext().all { (a, b) -> a.sweep < b.sweep })
        require(profiles.map { it.model.basicMassKg }.distinct().size == 1)
    }

    fun limits(fuelKg: Double?, sweep: Double?): LoadLimits? {
        if (profiles.size == 1) return profiles.single().model.limits(fuelKg)
        if (sweep == null || !sweep.isFinite() || sweep !in 0.0..1.0) return null
        if (sweep <= profiles.first().sweep) return profiles.first().model.limits(fuelKg)
        if (sweep >= profiles.last().sweep) return profiles.last().model.limits(fuelKg)
        val upper = profiles.indexOfFirst { it.sweep >= sweep }
        if (profiles[upper].sweep == sweep) return profiles[upper].model.limits(fuelKg)
        val a = profiles[upper - 1]; val b = profiles[upper]
        val left = a.model.limits(fuelKg) ?: return null
        val right = b.model.limits(fuelKg) ?: return null
        val fraction = (sweep - a.sweep) / (b.sweep - a.sweep)
        return LoadLimits(left.minimumG * (1 - fraction) + right.minimumG * fraction,
            left.maximumG * (1 - fraction) + right.maximumG * fraction)
    }
}

internal data class SweptStructuralLoadResult(val model: SweptStructuralLoadModel?, val issue: String? = null)

internal object SweptStructuralLoadExtractor {
    /** Null means no per-sweep load fields: retain the legacy fixed-load extraction path. */
    fun extract(document: BlkBlock, paths: List<String>, basicMassKg: Double?): SweptStructuralLoadResult? {
        val fields = document.fields()
        fun matches(path: String) = fields.filter { it.first.equals(path, true) }.map { it.second }
        if (paths.none { matches("$it.Strength.CritOverload").isNotEmpty() }) return null
        return try {
            require(basicMassKg != null) { "requires valid EmptyMass, OilMass and MaxNitro" }
            val profiles = paths.map { path ->
                val force = matches("$path.Strength.CritOverload").singleOrNull()
                require(force != null && force.type.equals("p2", true) && force.values.size == 2) {
                    "missing or ambiguous $path.Strength.CritOverload"
                }
                val negative = force.values[0].toDoubleOrNull()
                val positive = force.values[1].toDoubleOrNull()
                require(negative != null && positive != null && negative.isFinite() && positive.isFinite() && negative < 0 && positive > 0) {
                    "invalid $path.Strength.CritOverload"
                }
                val sweepFields = matches("$path.Sweep")
                val sweep = if (paths.size == 1 && sweepFields.isEmpty()) 0.0 else sweepFields.singleOrNull()
                    ?.takeIf { it.type.lowercase() in setOf("r", "i", "i64") }?.number()
                require(sweep != null && sweep.isFinite() && sweep in 0.0..1.0) { "invalid or ambiguous $path.Sweep" }
                StructuralLoadProfile(sweep, StructuralLoadModel(negative, positive, basicMassKg))
            }.sortedBy { it.sweep }
            require(profiles.map { it.sweep }.distinct().size == profiles.size) { "duplicate sweep positions" }
            SweptStructuralLoadResult(SweptStructuralLoadModel(profiles))
        } catch (e: IllegalArgumentException) {
            SweptStructuralLoadResult(null, "Structural load sweep table: ${e.message}")
        }
    }
}
