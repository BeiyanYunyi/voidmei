package voidmei.fm

import kotlin.math.sqrt

data class StallLiftProfile(val sweep: Double, val cleanArea: Double, val fullFlapArea: Double)

/** Quasi-steady 1 G IAS estimate using basic mass, sea-level density and FM lift factors. */
data class StallSpeedModel(val basicMassKg: Double, val profiles: List<StallLiftProfile>) {
    init {
        require(basicMassKg.isFinite() && basicMassKg > 0)
        require(profiles.isNotEmpty())
        require(profiles.all { it.sweep.isFinite() && it.sweep in 0.0..1.0 &&
            it.cleanArea.isFinite() && it.cleanArea > 0 && it.fullFlapArea.isFinite() && it.fullFlapArea > 0 })
        require(profiles.zipWithNext().all { (a, b) -> a.sweep < b.sweep })
    }

    fun speedKmh(fuelKg: Double?, flapsPercent: Double?, sweep: Double?): Double? {
        if (fuelKg == null || !fuelKg.isFinite() || fuelKg < 0 ||
            flapsPercent == null || !flapsPercent.isFinite() || flapsPercent !in 0.0..100.0) return null
        val mass = basicMassKg + fuelKg
        if (!mass.isFinite()) return null
        fun area(profile: StallLiftProfile) = profile.cleanArea * (1 - flapsPercent / 100) + profile.fullFlapArea * flapsPercent / 100
        val liftArea = if (profiles.size == 1) area(profiles.single()) else {
            if (sweep == null || !sweep.isFinite() || sweep !in 0.0..1.0) return null
            when {
                sweep <= profiles.first().sweep -> area(profiles.first())
                sweep >= profiles.last().sweep -> area(profiles.last())
                else -> {
                    val right = profiles.indexOfFirst { it.sweep > sweep }
                    val a = profiles[right - 1]; val b = profiles[right]
                    val fraction = (sweep - a.sweep) / (b.sweep - a.sweep)
                    area(a) * (1 - fraction) + area(b) * fraction
                }
            }
        }
        return (3.6 * sqrt((2 * mass * 9.80) / (1.225 * liftArea))).takeIf { it.isFinite() && it > 0 }
    }
}

internal data class StallSpeedResult(val model: StallSpeedModel?, val issue: String? = null)

internal object StallSpeedExtractor {
    fun extract(document: BlkBlock, wingPaths: List<Pair<String, Double>>): StallSpeedResult {
        val fields = document.fields()
        val blocks = mutableListOf<String>()
        fun visit(block: BlkBlock, prefix: String = "") {
            block.entries.filterIsInstance<BlkBlock>().forEach { child ->
                val path = if (prefix.isEmpty()) child.name else "$prefix.${child.name}"
                blocks += path
                visit(child, path)
            }
        }
        visit(document)
        var lastPath = "model"
        fun block(vararg paths: String): String {
            for (path in paths) {
                val exact = blocks.filter { it.equals(path, true) }
                val matches = exact.ifEmpty { blocks.filter { it.endsWith(".$path", true) } }
                if (matches.isEmpty()) continue
                return matches.singleOrNull() ?: error("Ambiguous block: $path")
            }
            error("Missing block: ${paths.first()}")
        }
        fun number(vararg paths: String, exactOnly: Boolean = false): Double {
            for (path in paths) {
                val exact = fields.filter { it.first.equals(path, true) }
                val matches = if (exactOnly) exact else exact.ifEmpty { fields.filter { it.first.endsWith(".$path", true) } }
                if (matches.isEmpty()) continue
                val field = matches.singleOrNull()?.second ?: error("Ambiguous $path")
                lastPath = path
                check(field.type.lowercase() in listOf("r", "i", "i64")) { "Invalid numeric type: $path" }
                return checkNotNull(field.number()) { "Invalid numeric value: $path" }.also { check(it.isFinite()) { "Non-finite value: $path" } }
            }
            error("Missing ${paths.first()}")
        }
        fun positive(value: Double) = value.also { check(it > 0) { "Expected positive value: $lastPath" } }
        fun nonnegative(value: Double) = value.also { check(it >= 0) { "Expected nonnegative value: $lastPath" } }
        return try {
            val mass = positive(number("Mass.EmptyMass", "EmptyMass")) +
                nonnegative(number("Mass.OilMass", "OilMass")) + nonnegative(number("Mass.MaxNitro", "MaxNitro"))
            val bodyArea = nonnegative(number("Areas.Fuselage", "FuselagePlane.Areas.Main"))
            val bodyPolar = if (bodyArea > 0) block("Fuselage", "FuselagePlane.Polar") else null
            val bodyAoA = if (bodyPolar != null) positive(number("$bodyPolar.alphaCritHigh", exactOnly = true)) else 1.0
            fun bodyLift(wingAoA: Double): Double {
                if (bodyArea == 0.0) return 0.0
                val coefficient = nonnegative(if (bodyAoA < wingAoA)
                    number("$bodyPolar.ClAfterCrit", exactOnly = true)
                else number("$bodyPolar.ClCritHigh", exactOnly = true))
                val scale = nonnegative(number("$bodyPolar.lineClCoeff", exactOnly = true))
                return bodyArea * coefficient * scale * wingAoA / bodyAoA
            }
            val profiles = wingPaths.map { (prefix, sweep) ->
                fun path(value: String) = if (prefix.isEmpty()) value else "$prefix.$value"
                val segments = listOf("LeftIn", "LeftMid", "LeftOut", "LeftCut", "RightIn", "RightMid", "RightOut", "RightCut", "Aileron")
                val wingArea = positive(segments.sumOf { segment ->
                    val legacy = if (segment == "Aileron") segment else "Wing$segment"
                    nonnegative(if (prefix.isEmpty()) number("Areas.$legacy") else number(path("Areas.$segment")))
                })
                fun lift(clean: Boolean): Double {
                    val polar = if (clean) "NoFlaps" else "FullFlaps"
                    val alternate = if (clean) "FlapsPolar0" else "FlapsPolar1"
                    val selected = block(path(polar), path(alternate))
                    val aoa = positive(number("$selected.alphaCritHigh", exactOnly = true))
                    val cl = positive(number("$selected.ClCritHigh", exactOnly = true))
                    return wingArea * cl + bodyLift(aoa)
                }
                StallLiftProfile(sweep, lift(true), lift(false))
            }.sortedBy { it.sweep }
            check(profiles.isNotEmpty()) { "Missing valid wing profiles" }
            check(profiles.map { it.sweep }.distinct().size == profiles.size) { "Duplicate wing sweep positions" }
            StallSpeedResult(StallSpeedModel(mass, profiles))
        } catch (e: IllegalStateException) { StallSpeedResult(null, e.message ?: "Invalid stall model") }
          catch (e: IllegalArgumentException) { StallSpeedResult(null, e.message ?: "Invalid stall model") }
    }
}
