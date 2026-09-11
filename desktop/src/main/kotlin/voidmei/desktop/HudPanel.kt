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
    header: @Composable () -> Unit,
) {
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density,
        systemDensity.fontScale * settings.hudFontScale),
        LocalReadingNumberFont provides remember(settings.hudNumberFont, settings.numberFont) {
            resolveHudNumberFont(settings.hudNumberFont ?: settings.numberFont).family
        },
        LocalReadingColumns provides settings.hudReadingColumns,
        LocalReadingColors provides readingColors(settings, hud = true)) {
        HudPanelContent(connection, settings, alerts, model, onContentHeightChanged, thermal, mapEndpoint, sharedMap, header)
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
    header: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val flight = connection as? ConnectionState.Flying
    val fields = HudField.selected(settings.hudFields)
    // Keep manual scrolling during ordinary samples, but start a new flight/layout at its first reading.
    val bodyScroll = key(flight != null, flight?.telemetry?.aircraft?.lowercase(), fields) { rememberScrollState() }
    var headerHeight by remember { mutableStateOf(-1) }
    var bodyHeight by remember { mutableStateOf(-1) }
    val reportHeight by rememberUpdatedState(onContentHeightChanged)
    LaunchedEffect(headerHeight, bodyHeight, density) {
        if (headerHeight >= 0 && bodyHeight >= 0) with(density) {
            reportHeight(headerHeight.toDp() + bodyHeight.toDp() + 40.dp)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().testTag("hud-panel")
        .background(Color(0xFF111820).copy(alpha = settings.hudOpacity)).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }) {
            Box(Modifier.fillMaxWidth().testTag("hud-header")) { header() }
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlightAlertPanel(alerts, compact = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(bodyScroll)
            .testTag("hud-body").onSizeChanged { bodyHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(statusText(connection), color = Color.White)
            flight?.let {
                FlightPanel(it, compact = true, fields = fields,
                    mechanization = settings.hudMechanization, model = model, thermal = thermal,
                    showGear = settings.hudGear, showFlaps = settings.hudFlaps, showAirbrake = settings.hudAirbrake, aoaBarWarningPercent = settings.hudAoaBarWarningPercent, aoaWarningPercent = settings.hudAoaWarningPercent, readingAlerts = alerts, showFlapBar = settings.hudFlapBar, compassHeadingUp = settings.hudCompassHeadingUp, hiddenLabels = settings.hudHiddenLabels, altitudeMode = settings.hudAltitudeMode)
                if (HudField.HEADING in fields && mapEndpoint != null) key(it.telemetry.aircraft) {
                    HudMapGrid(mapEndpoint, sharedMap)
                }
                settings.hudEngineIndex?.let { index -> key(it.telemetry.aircraft, index) { HudEnginePanel(it.telemetry.engines, index, fields = HudEngineField.selected(settings.hudEngineFields)) } }
                if (settings.hudAttitude) AttitudePanel(it.telemetry, compact = true, model = model, earthFixed = settings.hudAttitudeEarthFixed, showAoaLimits = settings.hudAttitudeAoaLimits)
            }
        }
    }
        if (flight != null && settings.hudCrosshair)
            if (settings.hudCrosshairImage.isEmpty()) CrosshairPanel(settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairRight)
            else ImageCrosshair(settings.hudCrosshairImage, settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairStretch, settings.hudCrosshairRight)
    }

}
