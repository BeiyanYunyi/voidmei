package voidmei.fm

enum class EnginePeakKind { SHAFT_POWER_HP, THRUST_KGF }
data class EnginePeakReference(val telemetryIndex: Int, val kind: EnginePeakKind, val peak: Double)

/** Compute off the UI thread once per selected FM/fuel; never in a telemetry frame. */
object EnginePeakExtractor {
    fun extract(document: BlkBlock, fuel: FuelModification? = null, checkActive: () -> Unit = {}): List<EnginePeakReference> {
        checkActive()
        val instances = EngineInstanceResolver.resolve(document)
        val effective = instances.document()
        val pistons = PistonParameterExtractor.extract(effective).engines.associateBy { it.source }
        val jets = JetThrustExtractor.extract(effective).engines.associateBy { it.source }
        return instances.engines.mapNotNull { instance ->
            checkActive()
            val name = instance.binding.instance
            val piston = pistons[name]
            val jet = jets[name]
            val kind: EnginePeakKind
            val peak: Double?
            if (piston != null && jet == null) {
                kind = EnginePeakKind.SHAFT_POWER_HP
                val model = try { PistonModelBuilder.build(piston, fuel = fuel) }
                    catch (_: IllegalArgumentException) { return@mapNotNull null }
                peak = model.wepStages?.let { PistonPowerModel.peakWepPower(it, checkActive) }
            } else if (jet != null && piston == null) {
                kind = EnginePeakKind.THRUST_KGF
                peak = jet.peakThrust(afterburner = true)
            } else return@mapNotNull null
            peak?.let { EnginePeakReference(instance.binding.telemetryIndex, kind, it) }
        }
    }
}
