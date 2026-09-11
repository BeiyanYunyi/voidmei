package voidmei.desktop

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.nio.file.Path
import voidmei.fm.*
import voidmei.telemetry.AircraftAlertModel

/** Loaded at application scope so hiding the settings window cannot suspend model loading. */
internal data class FlightModelSession(
    val state: FlightModelState,
    val detailResult: Result<FlightModelDetails>?,
    val parameterResult: Result<FlightModelCalculation>?,
    val fuelId: String?,
    val selectFuel: (String?) -> Unit,
    val reload: () -> Unit,
) {
    val alertModel: AircraftAlertModel?
        get() {
            val ready = state as? FlightModelState.Ready ?: return null
            val parameters = parameterResult?.getOrNull()?.parameters ?: return null
            return AircraftAlertModel(ready.aircraft, parameters)
        }
}

@Composable
internal fun rememberFlightModelSession(aircraft: String?, dataRoot: String,
    parameterExtractor: (BlkBlock, FuelModification?) -> FlightModelParameters = FlightModelExtractor::extract,
): FlightModelSession {
    var reload by remember { mutableStateOf(0) }
    val repository = remember(dataRoot, reload) { runCatching { FlightModelRepository(Path.of(dataRoot)) } }
    var state by remember(aircraft, dataRoot, reload) {
        mutableStateOf<FlightModelState>(aircraft?.let { FlightModelState.Loading(it) } ?: FlightModelState.Unresolved)
    }
    val ready = state as? FlightModelState.Ready
    var detailResult by remember(ready) { mutableStateOf<Result<FlightModelDetails>?>(null) }
    LaunchedEffect(ready) {
        if (ready != null) detailResult = withContext(Dispatchers.Default) {
            try { Result.success(FlightModelDetails.prepare(ready)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }
    val details = detailResult?.getOrNull()
    val fuels = details?.fuels
    var fuelId by remember(state) { mutableStateOf<String?>(null) }
    val fuel = fuels?.options?.firstOrNull { it.id == fuelId }
    var parameterResult by remember(ready, details, fuel) {
        mutableStateOf<Result<FlightModelCalculation>?>(null)
    }
    LaunchedEffect(ready, details, fuel) {
        if (ready != null && details != null) parameterResult = withContext(Dispatchers.Default) {
            try {
                val parameters = parameterExtractor(ready.document, fuel).copy(
                    enginePeaks = voidmei.fm.EnginePeakExtractor.extract(ready.document, fuel) { ensureActive() })
                val models = details.pistons.engines.map { engine ->
                    ensureActive()
                    try { Result.success(voidmei.fm.PistonModelBuilder.build(engine, fuel = fuel)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                }
                Result.success(FlightModelCalculation(parameters, models))
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }
    LaunchedEffect(aircraft, dataRoot, reload) {
        if (aircraft != null) {
            state = try {
                withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    repository.getOrThrow().load(aircraft) { context.ensureActive() }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { FlightModelState.Invalid(aircraft, e.message ?: "Cannot load FM") }
        }
    }
    return FlightModelSession(state, detailResult, parameterResult, fuelId, { fuelId = it }, { reload++ })
}
