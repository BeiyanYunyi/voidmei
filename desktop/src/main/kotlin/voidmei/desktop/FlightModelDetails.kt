package voidmei.desktop

import voidmei.fm.*

internal data class FlightModelCalculation(
    val parameters: FlightModelParameters,
    val pistonModels: List<Result<PistonModels>>,
)

/** Static display data, prepared once per loaded document away from the UI thread. */
internal data class FlightModelDetails(
    val fuels: FuelModificationResult?,
    val instances: EngineInstanceResult,
    val hasInstances: Boolean,
    val pistons: PistonParameterResult,
    val jets: JetThrustResult,
    val fields: List<Pair<String, BlkField>>,
) {
    companion object {
        fun prepare(ready: FlightModelState.Ready): FlightModelDetails {
            val instances = EngineInstanceResolver.resolve(ready.document)
            val hasInstances = ready.document.entries.any {
                it is BlkBlock && Regex("Engine[0-9]+", RegexOption.IGNORE_CASE).matches(it.name)
            }
            val engineDocument = if (hasInstances) instances.document() else ready.document
            return FlightModelDetails(ready.central?.let(FuelModificationExtractor::extract), instances,
                hasInstances, PistonParameterExtractor.extract(engineDocument),
                JetThrustExtractor.extract(engineDocument), ready.document.fields())
        }
    }
}
