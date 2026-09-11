package voidmei.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import voidmei.config.AppSettings
import voidmei.telemetry.*

/** Flight display shared by the overlay window and its layout regression tests. */
@Composable
internal fun HudPanel(
    connection: ConnectionState,
    settings: AppSettings,
    alerts: List<FlightAlert>,
    model: AircraftAlertModel?,
    onContentHeightChanged: (Dp) -> Unit = {},
    thermal: EngineThermalObservation? = null,
    mapEndpoint: String? = null,
    sharedMap: kotlinx.coroutines.flow.StateFlow<MapConnection>? = null,
    connectionLabel: String? = null,
    messages: HudMessageState? = null,
    header: @Composable () -> Unit,
) {
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density,
        systemDensity.fontScale * settings.hudFontScale),
        // The transparent HUD has no Material Surface to supply a light content color.
        androidx.compose.material3.LocalContentColor provides Color.White,
        LocalReadingNumberFont provides remember(settings.hudNumberFont, settings.numberFont) {
            resolveHudNumberFont(settings.hudNumberFont ?: settings.numberFont).family
        },
        LocalReadingColumns provides settings.hudReadingColumns,
        LocalReadingColors provides readingColors(settings, hud = true)) {
        val scene = settings.hudSceneLayout?.takeIf { it.enabled }
        if (scene != null) HudScene(connection, settings, scene, alerts, model, thermal, mapEndpoint, sharedMap, connectionLabel, messages)
        else HudPanelContent(connection, settings, alerts, model, onContentHeightChanged, thermal, mapEndpoint, sharedMap, connectionLabel, header)
    }
}

@Composable
private fun HudPanelContent(
    connection: ConnectionState,
    settings: AppSettings,
    alerts: List<FlightAlert>,
    model: AircraftAlertModel?,
    onContentHeightChanged: (Dp) -> Unit,
    thermal: EngineThermalObservation?,
    mapEndpoint: String?,
    sharedMap: kotlinx.coroutines.flow.StateFlow<MapConnection>?,
    connectionLabel: String?,
    header: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val flight = connection as? ConnectionState.Flying
    val fields = HudField.selected(settings.hudFields)
    // Keep manual scrolling during ordinary samples, but start a new flight/layout at its first reading.
    val engineFields = settings.hudEngineIndex?.let { HudEngineField.selected(settings.hudEngineFields) }
    val bodyScroll = key(flight != null, flight?.telemetry?.aircraft?.lowercase(), fields,
        settings.hudEngineIndex, engineFields, settings.hudReadingColumns) { rememberScrollState() }
    var headerHeight by remember { mutableStateOf(-1) }
    var titleHeight by remember { mutableStateOf(0) }
    var bodyHeight by remember { mutableStateOf(-1) }
    val reportHeight by rememberUpdatedState(onContentHeightChanged)
    LaunchedEffect(headerHeight, bodyHeight, density) {
        if (headerHeight >= 0 && bodyHeight >= 0) with(density) {
            reportHeight(headerHeight.toDp() + bodyHeight.toDp() + 40.dp)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Reserve two thirds of the space below the title for scrollable readings.
    val alertHeight = ((maxHeight - with(density) { titleHeight.toDp() } - 48.dp).coerceAtLeast(0.dp) / 3)
        .coerceAtMost(HUD_ALERT_HEIGHT_DP.dp)
    Column(Modifier.fillMaxSize().testTag("hud-panel")
        .background(Color(0xFF111820).copy(alpha = settings.hudOpacity)).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }) {
            Box(Modifier.fillMaxWidth().testTag("hud-header").onSizeChanged { titleHeight = it.height }) { header() }
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlightAlertPanel(alerts, compact = true, maximumHeight = alertHeight)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(end = 8.dp).verticalScroll(bodyScroll)
            .testTag("hud-body").onSizeChanged { bodyHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(connectionLabel ?: statusText(connection), color = Color.White)
            flight?.let {
                FlightPanel(it, compact = true, fields = fields,
                    mechanization = settings.hudMechanization, model = model, thermal = thermal,
                    showGear = settings.hudGear, showFlaps = settings.hudFlaps, showAirbrake = settings.hudAirbrake, aoaBarWarningPercent = settings.hudAoaBarWarningPercent, aoaWarningPercent = settings.hudAoaWarningPercent, readingAlerts = alerts, showFlapBar = settings.hudFlapBar, compassHeadingUp = settings.hudCompassHeadingUp, hiddenLabels = settings.hudHiddenLabels, altitudeMode = settings.hudAltitudeMode, pollingIntervalMs = settings.pollIntervalMs)
                if (HudField.HEADING in fields && mapEndpoint != null) key(it.telemetry.aircraft) {
                    HudMapGrid(mapEndpoint, sharedMap)
                }
                settings.hudEngineIndex?.let { index -> key(it.telemetry.aircraft, index) {
                    HudEnginePanel(it.telemetry.engines, index, fields = HudEngineField.selected(settings.hudEngineFields),
                        warnings = engineReadingWarnings(it, index, model, alerts, thermal),
                        heatBudget = thermal?.hudBudget(it, model, index),
                        powerPercent = it.enginePowerPercentReading(index, model))
                    if (HudEngineField.HEAT_BUDGET.id in settings.hudEngineFields)
                        ThermalBudgetStatusPanel(it, model, thermal, index)
                    CompressorStageBar(it, index, model, settings.hudEngineFields)
                    HudCompressorAdvice(it, index, model, settings.hudEngineFields)
                } }
                if (settings.hudAttitude) AttitudePanel(it.telemetry, compact = true, model = model, earthFixed = settings.hudAttitudeEarthFixed, showAoaLimits = settings.hudAttitudeAoaLimits)
            }
        }
        HudScrollIndicator(bodyScroll, Modifier.matchParentSize())
        }
    }
        if (flight != null && settings.hudCrosshair)
            if (settings.hudCrosshairImage.isEmpty()) CrosshairPanel(settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairRight)
            else ImageCrosshair(settings.hudCrosshairImage, settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairStretch, settings.hudCrosshairRight)
    }

}
