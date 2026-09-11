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

/** Local illustrative samples; never enter the live telemetry or recording pipeline. */
internal fun hudPreviewFlight(): ConnectionState.Flying {
    val telemetry = TelemetryParser.parse("""{
        "valid":true,"IAS, km/h":340,"TAS, km/h":360,"Mach":0.30,"H, m":1500,
        "Vy, m/s":5,"Ny":1.2,"AoA, deg":4,"Mfuel, kg":300,"Mfuel0, kg":600,
        "gear, %":0,"flaps, %":20,"airbrake, %":0,
        "throttle 1, %":95,"RPM 1":2400,"power 1, hp":900,"thrust 1, kgs":700,
        "water temp 1, C":95,"oil temp 1, C":80,
        "throttle 2, %":90,"RPM 2":2300,"power 2, hp":850,"thrust 2, kgs":650,
        "water temp 2, C":90,"oil temp 2, C":75
    }""", """{"valid":true,"type":"preview","aviahorizon_pitch":-5,"aviahorizon_roll":15,"compass":45}""")!!
    val calculator = FlightCalculator()
    calculator.update(telemetry, 0)
    val next = telemetry.copy(tasKmh = 363.6)
    return ConnectionState.Flying(next, calculator.update(next, 1000))
}

@Composable
internal fun HudLayoutPreview(settings: AppSettings) {
    val flight = remember { hudPreviewFlight() }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            val step = 20.dp.toPx()
            for (y in 0..(size.height / step).toInt()) for (x in 0..(size.width / step).toInt())
                drawRect(if ((x + y) % 2 == 0) Color(0xFF39434D) else Color(0xFF252D35),
                    Offset(x * step, y * step), Size(step, step))
        }
        HudPanel(flight, settings, emptyList(), null, connectionLabel = "示例数据 · 可在设置窗口继续调整") {
            Text("HUD 布局预览")
        }
    }
}

@Composable
internal fun HudLayoutPreviewWindow(settings: AppSettings, onClose: () -> Unit) {
    val state = rememberWindowState(width = settings.hudWidthDp.dp, height = 640.dp)
    LaunchedEffect(settings.hudWidthDp) {
        state.size = DpSize(settings.hudWidthDp.dp, state.size.height)
    }
    Window(onCloseRequest = onClose, state = state, title = "HUD 布局预览 · 示例数据") {
        MaterialTheme(colorScheme = darkColorScheme()) { HudLayoutPreview(settings) }
    }
}
