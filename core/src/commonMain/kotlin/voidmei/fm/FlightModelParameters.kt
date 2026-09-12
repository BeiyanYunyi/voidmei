package voidmei.fm

data class WingLimits(val vneKmh: Double?, val maxMach: Double?, val minAngleOfAttackDeg: Double?, val maxAngleOfAttackDeg: Double?)

data class WingConfiguration(
    val sweep: Double,
    val vneKmh: Double?,
    val maxMach: Double?,
    val cleanMinAoA: Double?,
    val cleanMaxAoA: Double?,
    val flapsMinAoA: Double?,
    val flapsMaxAoA: Double?,
) {
    fun limits(flapsPercent: Double?): WingLimits {
        fun blend(clean: Double?, extended: Double?): Double? {
            if (flapsPercent == null || !flapsPercent.isFinite() || flapsPercent !in 0.0..100.0) return null
            if (flapsPercent == 0.0) return clean
            if (flapsPercent == 100.0) return extended
            return if (clean != null && extended != null) clean + (extended - clean) * flapsPercent / 100 else null
        }
        return WingLimits(vneKmh, maxMach, blend(cleanMinAoA, flapsMinAoA), blend(cleanMaxAoA, flapsMaxAoA))
    }
}

/** FM structural limits, not a prediction of the game's damage timer or current modifications. */
data class FlightModelParameters(
    val emptyMassKg: Double?,
    val maximumFuelMassKg: Double?,
    val wings: List<WingConfiguration>,
    val variableSweep: Boolean,
    val issues: List<String>,
    val gearLimitKmh: Double? = null,
    val flapLimits: FlapLimits? = null,
    val structuralLoad: StructuralLoadModel? = null,
    val controlSpeeds: ControlEffectiveSpeeds = ControlEffectiveSpeeds(),
    val stallSpeed: StallSpeedModel? = null,
    val stallSpeedIssue: String? = null,
    val engineBindings: List<EngineBinding> = emptyList(),
    val engineRpmLimits: List<EngineRpmLimit> = emptyList(),
    val engineRpmReferences: List<EngineRpmReference> = emptyList(),
    val engineThermals: List<EngineThermalParameters> = emptyList(),
    val engineCompressors: Map<Int, PistonModels> = emptyMap(),
    val compressorFuel: FuelModification? = null,
    val basicMassKg: Double? = null,
    val enginePeaks: List<EnginePeakReference> = emptyList(),
    val wepFuel: WepFuelModel? = null,
    val sweptStructuralLoad: SweptStructuralLoadModel? = null,
    val controlPowerLoss: ControlPowerLoss = ControlPowerLoss(),
    val inertia: ModelInertia? = null,
) {
    fun loadLimits(fuelKg: Double?, sweep: Double?): LoadLimits? =
        if (sweptStructuralLoad != null) sweptStructuralLoad.limits(fuelKg, sweep) else structuralLoad?.limits(fuelKg)

    fun limits(sweep: Double?, flapsPercent: Double?): WingLimits? {
        if (wings.isEmpty()) return null
        if (!variableSweep) return wings.single().limits(flapsPercent)
        if (sweep == null || !sweep.isFinite() || sweep !in 0.0..1.0) return null
        if (sweep <= wings.first().sweep) return wings.first().limits(flapsPercent)
        if (sweep >= wings.last().sweep) return wings.last().limits(flapsPercent)
        val upper = wings.indexOfFirst { it.sweep >= sweep }
        if (wings[upper].sweep == sweep) return wings[upper].limits(flapsPercent)
        val left = wings[upper - 1]
        val right = wings[upper]
        val fraction = (sweep - left.sweep) / (right.sweep - left.sweep)
        val a = left.limits(flapsPercent)
        val b = right.limits(flapsPercent)
        fun blend(x: Double?, y: Double?) = if (x != null && y != null) x + fraction * (y - x) else null
        return WingLimits(blend(a.vneKmh, b.vneKmh), blend(a.maxMach, b.maxMach),
            blend(a.minAngleOfAttackDeg, b.minAngleOfAttackDeg), blend(a.maxAngleOfAttackDeg, b.maxAngleOfAttackDeg))
    }
}

object FlightModelExtractor {
    fun extract(document: BlkBlock, fuel: FuelModification? = null): FlightModelParameters {
        val issues = mutableListOf<String>()
        val fields = document.fields()
        fun number(vararg paths: String, positive: Boolean = false, equalPair: Boolean = false): Double? {
            for (path in paths) {
                val exact = fields.filter { it.first.equals(path, true) }
                val matches = exact.ifEmpty { fields.filter { it.first.endsWith(".$path", true) } }
                if (matches.isEmpty()) continue
                if (matches.size != 1) { issues += "Ambiguous field: $path"; return null }
                val field = matches.single().second
                // Equal elevator thresholds are independent of the undocumented pair ordering.
                // Do not apply this accommodation to unrelated scalar FM fields.
                val pair = if (equalPair && field.type == "p2" && field.values.size == 2)
                    field.values.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) } else null
                if (pair != null && pair.all { it != null && it > 0 } && pair[0] != pair[1]) {
                    issues += "Unsupported directional thresholds: $path (${field.values.joinToString()})"
                    return null
                }
                val value = if (pair != null) pair[0]?.takeIf { it == pair[1] }
                    else field.takeIf { it.type in listOf("r", "i", "i64") }?.number()
                if (value == null || (positive && value <= 0)) { issues += "Invalid field: $path"; return null }
                return value
            }
            return null
        }
        fun wing(prefix: String, sweep: Double, legacy: Boolean): WingConfiguration {
            fun paths(suffix: String) = if (prefix.isEmpty()) suffix else "$prefix.$suffix"
            return WingConfiguration(sweep,
                if (legacy) number("Vne", paths("Strength.VNE"), positive = true) else number(paths("Strength.VNE"), positive = true),
                if (legacy) number("VneMach", paths("Strength.MNE"), positive = true) else number(paths("Strength.MNE"), positive = true),
                number(paths("NoFlaps.alphaCritLow"), paths("FlapsPolar0.alphaCritLow")),
                number(paths("NoFlaps.alphaCritHigh"), paths("FlapsPolar0.alphaCritHigh")),
                number(paths("FullFlaps.alphaCritLow"), paths("FlapsPolar1.alphaCritLow")),
                number(paths("FullFlaps.alphaCritHigh"), paths("FlapsPolar1.alphaCritHigh")))
        }
        val sweepPaths = mutableListOf<String>()
        fun blocks(block: BlkBlock, parent: String = "") {
            block.entries.filterIsInstance<BlkBlock>().forEach {
                val path = if (parent.isEmpty()) it.name else "$parent.${it.name}"
                if (Regex("WingPlaneSweep[0-9]+", RegexOption.IGNORE_CASE).matches(it.name)) sweepPaths += path
                blocks(it, path)
            }
        }
        blocks(document)
        val variable = sweepPaths.size > 1
        val wings = if (sweepPaths.isNotEmpty()) {
            val extracted = sweepPaths.mapNotNull { path ->
                val hasSweep = fields.any { it.first.equals("$path.Sweep", true) }
                val sweep = if (!variable && !hasSweep) 0.0 else number("$path.Sweep")
                if (sweep == null || sweep !in 0.0..1.0) { issues += "Invalid sweep: $path"; null }
                else wing(path, sweep, false)
            }.sortedBy { it.sweep }
            if (extracted.size != sweepPaths.size || extracted.map { it.sweep }.distinct().size != extracted.size) {
                issues += "Incomplete or duplicate sweep table"
                emptyList()
            } else extracted
        } else {
            val prefix = if (fields.any { it.first.contains("WingPlane.", true) }) "WingPlane" else ""
            listOf(wing(prefix, 0.0, true))
        }
        val flaps = FlapLimitExtractor.extract(document)
        issues += flaps.issues
        val gearLimit = number("GearDestructionIndSpeed", positive = true)
        val emptyMass = number("Mass.EmptyMass", "EmptyMass", positive = true)
        val oil = number("Mass.OilMass", "OilMass")
        val nitro = number("Mass.MaxNitro", "MaxNitro")
        val basicMass = if (emptyMass != null && oil != null && oil >= 0 && nitro != null && nitro >= 0)
            (emptyMass + oil + nitro).takeIf { it.isFinite() && it > 0 } else null
        val sweptLoad = SweptStructuralLoadExtractor.extract(document, sweepPaths, basicMass)
        sweptLoad?.issue?.let(issues::add)
        var structuralLoad: StructuralLoadModel? = null
        val strengthPath = listOf("WingCritOverload", "Strength.CritOverload").firstOrNull { path ->
            fields.any { it.first.equals(path, true) || it.first.endsWith(".$path", true) }
        }
        if (strengthPath != null && sweptLoad == null) {
            val exact = fields.filter { it.first.equals(strengthPath, true) }
            val matches = exact.ifEmpty { fields.filter { it.first.endsWith(".$strengthPath", true) } }
            val field = matches.singleOrNull()?.second
            val forces = field?.values?.map { it.toDoubleOrNull() }
            if (field?.type?.equals("p2", true) != true || forces?.size != 2 ||
                forces.any { it == null || !it.isFinite() } || forces[0]!! >= 0 || forces[1]!! <= 0) {
                issues += "Invalid or ambiguous structural load: $strengthPath"
            } else if (basicMass == null) {
                issues += "Structural load requires valid EmptyMass, OilMass and MaxNitro"
            } else structuralLoad = StructuralLoadModel(forces[0]!!, forces[1]!!, basicMass)
        }
        val controlSpeeds = ControlEffectiveSpeeds(number("AileronEffectiveSpeed", positive = true),
            number("ElevatorsEffectiveSpeed", positive = true, equalPair = true), number("RudderEffectiveSpeed", positive = true))
        val stallPaths = if (sweepPaths.isNotEmpty()) sweepPaths.mapNotNull { path ->
            val sweep = if (sweepPaths.size == 1 && fields.none { it.first.equals("$path.Sweep", true) }) 0.0 else number("$path.Sweep")
            sweep?.let { path to it }
        }.takeIf { it.size == sweepPaths.size }.orEmpty()
        else listOf((if (fields.any { it.first.contains("WingPlane.", true) }) "WingPlane" else "") to 0.0)
        val stallSpeed = StallSpeedExtractor.extract(document, stallPaths)
        val bindings = EngineBindingExtractor.extract(document)
        issues += bindings.issues
        val instances = EngineInstanceResolver.resolve(document)
        val rpm = EngineRpmLimitExtractor.extract(instances)
        issues += rpm.issues
        val thermal = EngineThermalExtractor.extract(instances)
        issues += thermal.issues
        val pistonResult = PistonParameterExtractor.extract(instances.document())
        issues += pistonResult.issues
        val piston = pistonResult.engines.associateBy { it.source }
        val compressors = instances.engines.mapNotNull { instance ->
            val raw = piston[instance.binding.instance]?.takeIf { it.stages.size > 1 } ?: return@mapNotNull null
            val model = try { PistonModelBuilder.build(raw, fuel = fuel) } catch (e: IllegalArgumentException) {
                issues += "${instance.binding.instance}: 增压器换挡模型不可用：${e.message}"
                return@mapNotNull null
            }
            model.wepIssue?.let { issues += "${instance.binding.instance}: WEP 增压器换挡模型不可用：$it" }
            instance.binding.telemetryIndex to model
        }.toMap()
        val inertia = ModelInertiaExtractor.extract(document)
        issues += inertia.issues
        val powerLoss = ControlPowerLoss(number("AileronPowerLoss"), number("ElevatorPowerLoss"), number("RudderPowerLoss"))
        return FlightModelParameters(emptyMass,
            number("Mass.MaxFuelMass0", "MaxFuelMass0", positive = true), wings, variable, issues.distinct(), gearLimit, flaps.limits, structuralLoad, controlSpeeds, stallSpeed.model, stallSpeed.issue, bindings.bindings, rpm.limits, rpm.references, thermal.engines, compressors, fuel, basicMass, wepFuel = WepFuelExtractor.extract(document), sweptStructuralLoad = sweptLoad?.model,
            controlPowerLoss = powerLoss, inertia = inertia.inertia)
    }
}
