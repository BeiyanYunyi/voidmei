package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.nio.file.Path
import voidmei.telemetry.Telemetry
import voidmei.fm.FlightModelExtractor
import java.util.Locale
import voidmei.config.ModelDetailSection

internal fun fuelName(id: String) = when (id) {
    "ussr_fuel_b-95" -> "B-95"
    "ussr_fuel_b-100" -> "B-100"
    "150_octan_fuel" -> "150 辛烷"
    "100_octan_spitfire" -> "喷火 100 辛烷"
    else -> id
}

@Composable
internal fun FlightModelPanel(telemetry: Telemetry?, dataRoot: String,
    onModel: (String?, voidmei.fm.FlightModelParameters?) -> Unit, onDataRoot: (String) -> Unit,
    parameterExtractor: (voidmei.fm.BlkBlock, voidmei.fm.FuelModification?) -> voidmei.fm.FlightModelParameters = FlightModelExtractor::extract,
    aircraftOverride: String? = null, showDirectoryControls: Boolean = true,
    onSnapshot: (NamedModel?) -> Unit = {}, session: FlightModelSession? = null,
    hiddenSections: Set<ModelDetailSection> = emptySet(), onHiddenSections: ((Set<ModelDetailSection>) -> Unit)? = null) {
    val modelSession = session ?: rememberFlightModelSession(aircraftOverride ?: telemetry?.aircraft, dataRoot, parameterExtractor)
    var draft by remember(dataRoot) { mutableStateOf(dataRoot) }
    var filter by remember { mutableStateOf("") }
    val state = modelSession.state
    val ready = state as? FlightModelState.Ready
    val detailResult = modelSession.detailResult
    val parameterResult = modelSession.parameterResult
    val details = detailResult?.getOrNull()
    val fuels = details?.fuels
    val fuelId = modelSession.fuelId
    val fuel = fuels?.options?.firstOrNull { it.id == fuelId }
    val calculation = parameterResult?.getOrNull()
    val selectedParameters = calculation?.parameters
    // Scope retained inputs to the loaded model and fuel calculation, outside display branches.
    val curveStates = key(ready, calculation) { rememberSaveableStateHolder() }
    LaunchedEffect(ready, selectedParameters, details) {
        onModel(ready?.aircraft?.takeIf { selectedParameters != null }, selectedParameters)
        onSnapshot(if (ready != null && selectedParameters != null && details != null)
            NamedModel(ready.aircraft, selectedParameters, details.jets.engines,
                ready.dataDirectory?.let { Path.of(it).resolve(ready.source).toString() } ?: ready.source,
                java.time.Instant.now()) else null)
    }
    Text("气动模型文件", style = MaterialTheme.typography.titleLarge)
    onHiddenSections?.let { ModelDetailVisibility(hiddenSections, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showDirectoryControls) {
        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), label = { Text("解包数据或 flightmodels 目录") }, singleLine = true)
        Button(enabled = draft.isNotBlank(), onClick = { onDataRoot(draft) }) { Text("应用") }
        }
        TextButton(onClick = modelSession.reload) { Text("重新加载") }
    }
    if (!fuels?.options.isNullOrEmpty()) {
        Text("燃油估算方案（用于功率曲线和增压器提示，需与游戏实际选择核对）")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(fuelId == null, { modelSession.selectFuel(null) }, label = { Text("不追加修正") })
            fuels?.options?.forEach { option ->
                FilterChip(fuelId == option.id, { modelSession.selectFuel(option.id) }, label = { Text(fuelName(option.id)) })
            }
        }
    }
    if (ready != null && selectedParameters == null) {
        val failure = parameterResult?.exceptionOrNull() ?: detailResult?.exceptionOrNull()
        if (failure == null) Text("正在计算 ${ready.aircraft} 模型参数…")
        else Text("${ready.aircraft} 模型参数不可用：${failure.message}", color = MaterialTheme.colorScheme.error)
        return
    }
    when (val current = state) {
        FlightModelState.Unresolved -> Text("进入飞行后自动加载当前机型。")
        is FlightModelState.Loading -> Text("正在加载 ${current.aircraft}…")
        is FlightModelState.Missing -> Text("未找到 ${current.aircraft}：${current.path}")
        is FlightModelState.Invalid -> Text("${current.aircraft} 模型不可用：${current.reason}", color = MaterialTheme.colorScheme.error)
        is FlightModelState.Ready -> {
            current.dataDirectory?.let { Text("已识别模型目录：$it", style = MaterialTheme.typography.bodySmall) }
            val parameters = requireNotNull(selectedParameters)
            val limits = parameters.limits(telemetry?.wingSweepRatio, telemetry?.flapsPercent)
            fun Double?.display() = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
            if (ModelDetailSection.WEIGHT !in hiddenSections) {
                Text("模型空重 ${parameters.emptyMassKg.display()} kg · 燃油容量 ${parameters.maximumFuelMassKg.display()} kg")
            }
            if (ModelDetailSection.FLAPS !in hiddenSections) {
                ModelFlapLimitsTable(parameters.flapLimits)
                Text("当前襟翼开度的模型速度限制 ${parameters.flapLimits?.speedAt(telemetry?.flapsPercent).display()} km/h")
                Text("当前 IAS 下表内最大襟翼开度 ${parameters.flapLimits?.maximumPercentAt(telemetry?.iasKmh).display()} %")
                Text("模型起落架速度限制 ${parameters.gearLimitKmh.display()} km/h")
            }
            if (ModelDetailSection.CONTROLS !in hiddenSections) {
                Text("舵效衰减速度：副翼 ${parameters.controlSpeeds.aileronKmh.display()} · 升降舵 ${parameters.controlSpeeds.elevatorKmh.display()} · 方向舵 ${parameters.controlSpeeds.rudderKmh.display()} km/h")
            }
            if (ModelDetailSection.STALL !in hiddenSections) {
                Text("基础质量 1 G 失速 IAS 估算 ${parameters.stallSpeed?.speedKmh(telemetry?.fuelKg, telemetry?.flapsPercent, telemetry?.wingSweepRatio).display()} km/h")
                parameters.stallSpeedIssue?.let { Text("失速模型不可用：$it", style = MaterialTheme.typography.bodySmall) }
                if (parameters.stallSpeed != null) Text("准稳态模型估算，未计外挂载荷、损伤和机动过载。", style = MaterialTheme.typography.bodySmall)
            }
            if (ModelDetailSection.LOAD_LIMITS !in hiddenSections) {
                val loadLimits = parameters.loadLimits(telemetry?.fuelKg, telemetry?.wingSweepRatio)
                Text("基础质量估算过载范围 ${loadLimits?.minimumG.display()} ～ ${loadLimits?.maximumG.display()} G")
                if (parameters.structuralLoad != null || parameters.sweptStructuralLoad != null) Text("按模型空重、油液、加力剂及当前燃油估算，未计外挂载荷。", style = MaterialTheme.typography.bodySmall)
                if ((parameters.sweptStructuralLoad?.profiles?.size ?: 0) > 1) Text("后掠位置之间采用线性估算；缺少后掠数据时不显示过载限制。", style = MaterialTheme.typography.bodySmall)
            }
            if (ModelDetailSection.FLIGHT_LIMITS !in hiddenSections) {
                Text("模型 VNE ${limits?.vneKmh.display()} km/h · 马赫限制 ${limits?.maxMach.display()}")
                Text("迎角范围 ${limits?.minAngleOfAttackDeg.display()}° 至 ${limits?.maxAngleOfAttackDeg.display()}° · 后掠 ${(telemetry?.wingSweepRatio?.times(100)).display()}%")
            }
            if (parameters.issues.isNotEmpty()) Text("部分模型字段不可用：${parameters.issues.joinToString("；")}", color = MaterialTheme.colorScheme.error)
            Text("限制按当前襟翼和后掠位置估算，尚未计入改装修正及游戏损伤机制。", style = MaterialTheme.typography.bodySmall)
            val prepared = requireNotNull(details)
            val instances = prepared.instances
            val hasInstances = prepared.hasInstances
            if (hasInstances && instances.issues.isNotEmpty()) Text("实例参数不可用：${instances.issues.joinToString("；")}", color = MaterialTheme.colorScheme.error)
            if (parameters.engineBindings.isNotEmpty()) {
                Text("发动机实例对应", style = MaterialTheme.typography.titleMedium)
                parameters.engineBindings.forEach { binding ->
                    Text("#${binding.telemetryIndex} · ${binding.instance} → ${binding.parameterSource} · ${binding.type}" +
                        if (binding.hasInstanceOverrides) {
                            if (instances.engines.any { it.binding.telemetryIndex == binding.telemetryIndex }) " · 已按字段合并实例覆盖" else " · 实例覆盖不可用"
                        } else "")
                }
            }
            if (ModelDetailSection.THERMAL !in hiddenSections) EngineThermalPanel(parameters.engineThermals)
            parameters.engineRpmReferences.forEach { reference ->
                Text("#${reference.telemetryIndex} 模型满油门转速基准 ${reference.nominalRpm.display()} RPM")
            }
            parameters.engineRpmLimits.forEach { limit ->
                Text("#${limit.telemetryIndex} 模型最高允许转速 ${limit.maximumRpm.display()} RPM")
            }
            telemetry?.engines?.filter { engine -> parameters.engineBindings.none { it.telemetryIndex == engine.index } }?.forEach {
                Text("#${it.index} · 发动机模型对应未知", style = MaterialTheme.typography.bodySmall)
            }
            if (ModelDetailSection.PISTON !in hiddenSections) {
                val engines = prepared.pistons
                val fuelLabel = fuel?.let { fuelName(it.id) + if (it.inverted) "（高辛烷默认，不追加增益）" else "" } ?: "未选择燃油修正"
                fuels?.issues?.takeIf { it.isNotEmpty() }?.let { Text("燃油参数不可用：${it.joinToString("；")}", color = MaterialTheme.colorScheme.error) }
                engines.engines.forEachIndexed { engineIndex, engine ->
                    Text("${engine.source} · ${engine.type} · ${if (hasInstances) "实例发动机参数" else "类型发动机参数"}")
                    Text("海平面功率 ${engine.deckPowerHp.display()} hp · 军用/WEP 转速 ${engine.militaryRpm.display()} / ${engine.wepRpm.display()} RPM")
                    engine.stages.forEachIndexed { index, stage ->
                        Text("增压器 ${index + 1}：${stage.altitudeM.display()} m · ${stage.powerHp.display()} hp")
                    }
                    val model = requireNotNull(calculation).pistonModels[engineIndex]
                    val built = model.getOrNull()
                    val altitude = telemetry?.altitudeM
                    val speed = telemetry?.tasKmh
                    if (built != null) {
                        val military = if (altitude != null && speed != null)
                            voidmei.fm.PistonPowerModel.optimalPower(built.military.stages, altitude, speedKmh = speed) else null
                        val wep = if (altitude != null && speed != null) built.wepStages?.let {
                            voidmei.fm.PistonPowerModel.optimalPower(it, altitude, wep = true, speedKmh = speed)
                        } else null
                        Text("当前高度/TAS 模型功率：军用 ${military?.powerHp.display()} · WEP ${wep?.powerHp.display()} hp/台")
                        Text("按海平面 15°C 估算 · $fuelLabel；未计入损伤或实时油门。", style = MaterialTheme.typography.bodySmall)
                        built.wepIssue?.let { Text("WEP 不可用：$it", color = MaterialTheme.colorScheme.error) }
                        curveStates.SaveableStateProvider("piston-$engineIndex") { PowerCurvePanel(built, fuelLabel) }
                    } else Text("功率模型不可用：${model.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error)
            }
            if (engines.issues.isNotEmpty()) Text("发动机参数不可用：${engines.issues.joinToString("；")}", color = MaterialTheme.colorScheme.error)
            }
            if (ModelDetailSection.JET !in hiddenSections) {
                val jets = prepared.jets
                jets.engines.forEachIndexed { jetIndex, jet ->
                    val altitude = telemetry?.altitudeM
                    val speed = telemetry?.tasKmh
                    val military = if (altitude != null && speed != null) jet.thrust(altitude, speed) else null
                    val afterburner = if (altitude != null && speed != null) jet.thrust(altitude, speed, true) else null
                    Text("${jet.source} · 喷气推力表 ${jet.altitudesM.size} × ${jet.velocitiesKmh.size}")
                    Text("当前高度/TAS 查表：军用 ${military.display()} · 加力 ${afterburner.display()} kgf/台")
                    Text("范围 ${jet.altitudesM.first().display()}–${jet.altitudesM.last().display()} m / ${jet.velocitiesKmh.first().display()}–${jet.velocitiesKmh.last().display()} km/h；范围外或缺失点不估算。", style = MaterialTheme.typography.bodySmall)
                    curveStates.SaveableStateProvider("jet-$jetIndex") { JetThrustCurvePanel(jet) }
            }
            if (jets.issues.isNotEmpty()) Text("喷气推力表不可用：${jets.issues.joinToString("；")}", color = MaterialTheme.colorScheme.error)
            }
            val fields = prepared.fields
            Text("${current.aircraft} · ${current.source} · ${fields.size} 个原始字段")
            if (ModelDetailSection.RAW !in hiddenSections) ModelFieldsPanel(fields, filter) { filter = it }
            Text("可查看原始字段、模型限制及功率／推力估算；计算结果需结合来源和缺失提示理解。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
