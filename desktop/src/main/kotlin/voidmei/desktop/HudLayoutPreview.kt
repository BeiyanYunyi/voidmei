package voidmei.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
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
        "throttle 1, %":95,"RPM 1":2400,"power 1, hp":900,"thrust 1, kgs":700,
        "water temp 1, C":95,"oil temp 1, C":80,
        "throttle 2, %":90,"RPM 2":2300,"power 2, hp":850,"thrust 2, kgs":650,
        "water temp 2, C":90,"oil temp 2, C":75
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
internal fun HudLayoutPreview(settings: AppSettings, warnings: Boolean = false, missing: Boolean = false) {
    val flight = remember(warnings, missing) { hudPreviewFlight(warnings, missing) }
    val model = remember { hudPreviewModel() }
    val thermal = remember(flight, model) { EngineThermalMonitor().update(flight, model, 0) }
    val alerts = remember(flight, model, thermal) {
        FlightAlerts().updateForAircraft(flight, model, 0, false, thermalObservation = thermal).active
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            val step = 20.dp.toPx()
            for (y in 0..(size.height / step).toInt()) for (x in 0..(size.width / step).toInt())
                drawRect(if ((x + y) % 2 == 0) Color(0xFF39434D) else Color(0xFF252D35),
                    Offset(x * step, y * step), Size(step, step))
        }
        HudPanel(flight, settings, alerts, model, thermal = thermal,
            connectionLabel = "示例数据与模型 · 可在设置窗口继续调整") {
            Text("HUD 布局预览")
        }
    }
}

@Composable
internal fun HudLayoutPreviewWindow(settings: AppSettings, activationRequest: Int, onClose: () -> Unit) {
    var warnings by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    var nativeWindow by remember { mutableStateOf<java.awt.Frame?>(null) }
    val state = rememberWindowState(width = settings.hudWidthDp.dp, height = 640.dp)
    LaunchedEffect(settings.hudWidthDp) {
        state.size = DpSize(settings.hudWidthDp.dp, state.size.height)
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
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!warnings && !missing, { warnings = false; missing = false }, label = { Text("正常读数") })
                        FilterChip(warnings && !missing, { warnings = true; missing = false }, label = { Text("告警示例") })
                        FilterChip(missing, { warnings = false; missing = true }, label = { Text("缺失数据") })
                    }
                    Box(Modifier.weight(1f)) { HudLayoutPreview(settings, warnings, missing) }
                }
            }
        }
    }
}
