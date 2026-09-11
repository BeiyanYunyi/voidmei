package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import voidmei.config.HudRegionContent
import voidmei.config.AppSettings
import voidmei.telemetry.*
import voidmei.fm.*

/** Local illustrative samples; never enter the live telemetry or recording pipeline. */
internal fun hudPreviewFlight(warnings: Boolean = false, missing: Boolean = false): ConnectionState.Flying {
    if (missing) return ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""",
        """{"valid":true,"type":"preview"}""")!!.copy(engines = (1..2).map {
            Engine(it, null, null, null, null, null, null)
        }), FlightMetrics())
    val telemetry = TelemetryParser.parse("""{
        "valid":true,"IAS, km/h":340,"TAS, km/h":360,"Mach":0.30,"H, m":1500,
        "Vy, m/s":5,"Ny":1.2,"AoA, deg":4,"Mfuel, kg":300,"Mfuel0, kg":600,
        "gear, %":0,"flaps, %":20,"airbrake, %":0,
        "aileron, %":0,"elevator, %":20,"rudder, %":-10,
        "throttle 1, %":95,"RPM 1":2400,"power 1, hp":900,"thrust 1, kgs":700,
        "water temp 1, C":95,"oil temp 1, C":80,
        "RPM throttle 1, %":80,"mixture 1, %":100,"radiator 1, %":35,"oil radiator 1, %":20,
        "throttle 2, %":90,"RPM 2":2300,"power 2, hp":850,"thrust 2, kgs":650,
        "water temp 2, C":90,"oil temp 2, C":75,
        "RPM throttle 2, %":70,"mixture 2, %":90,"radiator 2, %":50,"oil radiator 2, %":40
    }""", """{"valid":true,"type":"preview","aviahorizon_pitch":-5,"aviahorizon_roll":15,"compass":45}""")!!
    val next = if (warnings) telemetry.copy(iasKmh = 510.0, tasKmh = 550.0, mach = .95,
        angleOfAttackDeg = 16.0, fuelKg = 30.0, engines = telemetry.engines.map {
            it.copy(rpm = 3200.0, waterTemperatureC = 110.0, oilTemperatureC = 95.0)
        }) else telemetry.copy(tasKmh = 363.6)
    val calculator = FlightCalculator()
    var metrics = FlightMetrics()
    // Supply the same ten-second warm-up as live fuel estimates, without waiting or running a poller.
    for (second in 0..10) metrics = calculator.update(next.copy(
        tasKmh = next.tasKmh!! - (10 - second) * 3.6,
        fuelKg = next.fuelKg!! + (10 - second)), second * 1000L)
    return ConnectionState.Flying(next, metrics)
}

/** Deliberately synthetic limits for checking presentation, never loaded into the live model session. */
private fun hudPreviewModel() = AircraftAlertModel("preview", FlightModelParameters(null, null,
    listOf(WingConfiguration(0.0, 500.0, .9, -10.0, 15.0, -8.0, 18.0)), false, emptyList(),
    engineRpmLimits = (1..2).map { EngineRpmLimit(it, 3000.0) },
    engineThermals = (1..2).map { EngineThermalParameters(it, listOf(EngineThermalBand(1, 100.0, 85.0, 200.0, 100.0))) }))

@Composable
internal fun HudLayoutPreview(settings: AppSettings, warnings: Boolean = false, missing: Boolean = false,
    onRegionMove: ((String, Int, Int) -> Unit)? = null,
    onRegionResize: ((String, Int, Int) -> Unit)? = null, dragTargetId: String? = null, denseMessages: Boolean = false, allAlerts: Boolean = false) {
    val flight = remember(warnings, missing) { hudPreviewFlight(warnings, missing) }
    val map = remember(missing) { kotlinx.coroutines.flow.MutableStateFlow<MapConnection>(
        if (missing) MapConnection.Waiting else MapConnection.Available(hudPreviewMap())) }
    val model = remember { hudPreviewModel() }
    val thermal = remember(flight, model) { EngineThermalMonitor().update(flight, model, 0) }
    val alerts = remember(flight, model, thermal, allAlerts, missing) {
        if (allAlerts && !missing) FlightAlert.entries.filter { it != FlightAlert.CONNECTION_READY }
        else FlightAlerts().updateForAircraft(flight, model, 0, false, thermalObservation = thermal).active
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            val step = 20.dp.toPx()
            for (y in 0..(size.height / step).toInt()) for (x in 0..(size.width / step).toInt())
                drawRect(if ((x + y) % 2 == 0) Color(0xFF39434D) else Color(0xFF252D35),
                    Offset(x * step, y * step), Size(step, step))
        }
        HudPanel(flight, settings, alerts, model, thermal = thermal, sharedMap = map,
            messages = HudMessageState(if (missing) emptyList() else if (denseMessages) (1..20).flatMap { id -> listOf(
                HudMessage(HudMessageKind.EVENT, id, "示例事件消息 $id"),
                HudMessage(HudMessageKind.DAMAGE, id, "示例损伤消息 $id")) } else listOf(
                HudMessage(HudMessageKind.EVENT, 1, "示例事件消息"), HudMessage(HudMessageKind.DAMAGE, 1, "示例损伤消息"))),
            connectionLabel = "示例数据与模型 · 可在设置窗口继续调整") {
            Text("HUD 布局预览")
        }
        settings.hudSceneLayout?.takeIf { it.enabled && onRegionMove != null }?.let { scene ->
            HudSceneDragOverlay(scene, onRegionMove!!, onRegionResize, dragTargetId)
        }
    }
}

@Composable
internal fun HudLayoutPreviewWindow(settings: AppSettings, activationRequest: Int,
    onSettingsChange: ((AppSettings) -> Unit)? = null, onClose: () -> Unit) {
    val typography = remember(settings.textFont) { textTypography(resolveTextFont(settings.textFont).family) }
    var warnings by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var denseMessages by remember { mutableStateOf(false) }
    var allAlerts by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableStateOf<String?>(null) }
    val regions = settings.hudSceneLayout?.regions.orEmpty()
    val showAllAlerts = allAlerts && settings.hudSceneLayout?.enabled == true &&
        regions.any { it.visible && it.content == HudRegionContent.ALERTS }
    LaunchedEffect(regions.map { it.id }) {
        if (regions.none { it.id == dragTarget }) dragTarget = null
    }
    var nativeWindow by remember { mutableStateOf<java.awt.Frame?>(null) }
    val previewWidth = settings.hudSceneLayout?.takeIf { it.enabled }?.width?.coerceAtMost(1100) ?: settings.hudWidthDp
    val state = rememberWindowState(width = previewWidth.dp, height = 640.dp)
    LaunchedEffect(previewWidth) {
        state.size = DpSize(previewWidth.dp, state.size.height)
    }
    // Hidden/minimized windows may pause their own recomposer; handle activation in the caller's scope.
    LaunchedEffect(activationRequest, nativeWindow) {
        nativeWindow?.let {
            state.isMinimized = false
            restoreDesktopWindow(it)
        }
    }
    Window(onCloseRequest = onClose, state = state, title = "HUD 布局预览 · 示例数据") {
        SideEffect { nativeWindow = window }
        MaterialTheme(typography = typography, colorScheme = hudColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!warnings && !missing, { warnings = false; missing = false }, label = { Text("正常读数") })
                        FilterChip(warnings && !missing, { warnings = true; missing = false }, label = { Text("告警示例") })
                        FilterChip(missing, { warnings = false; missing = true }, label = { Text("缺失数据") })
                        if (settings.hudSceneLayout?.let { it.enabled && it.regions.any { region -> region.visible && region.content == HudRegionContent.MESSAGES } } == true)
                            FilterChip(denseMessages, { denseMessages = !denseMessages }, enabled = !missing,
                                label = { Text("密集消息") }, modifier = Modifier.testTag("hud-preview-dense-messages"))
                        if (settings.hudSceneLayout?.let { it.enabled && it.regions.any { region -> region.visible && region.content == HudRegionContent.ALERTS } } == true)
                            FilterChip(allAlerts, { allAlerts = !allAlerts }, enabled = !missing,
                                label = { Text("全部告警样例") }, modifier = Modifier.testTag("hud-preview-all-alerts"))
                        if (settings.hudSceneLayout?.enabled == true && onSettingsChange != null)
                            FilterChip(editing, { editing = !editing }, label = { Text("拖动区域") },
                                modifier = Modifier.testTag("hud-preview-edit-regions"))
                    }
                    if (showAllAlerts && !missing) Text("全部告警样例仅用于布局检查，不代表这些告警会同时触发。", Modifier.padding(horizontal = 12.dp))
                    if (editing && settings.hudSceneLayout?.enabled == true) {
                        HudDragTargetSettings(regions, dragTarget) { dragTarget = it }
                        Text("拖动区域以移动，拖动右下角方块调整大小；指定目标可编辑被遮挡区域。修改自动保存。", Modifier.padding(horizontal = 12.dp))
                    }
                    Box(Modifier.weight(1f)) { HudLayoutPreview(settings, warnings, missing,
                        onRegionMove = if (editing && onSettingsChange != null) { id, x, y ->
                            settings.hudSceneLayout?.let { onSettingsChange(settings.copy(hudSceneLayout = it.moveRegion(id, x, y))) }
                        } else null,
                        onRegionResize = if (editing && onSettingsChange != null) { id, width, height ->
                            settings.hudSceneLayout?.let { onSettingsChange(settings.copy(hudSceneLayout = it.resizeRegion(id, width, height))) }
                        } else null, dragTargetId = dragTarget, denseMessages = denseMessages, allAlerts = showAllAlerts) }
                }
            }
        }
    }
}
