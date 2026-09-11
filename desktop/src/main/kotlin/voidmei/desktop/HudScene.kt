package voidmei.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import voidmei.config.*
import voidmei.telemetry.*

/** All regions are Compose children of the existing HUD window, including their alpha layers. */
@Composable
internal fun HudScene(connection: ConnectionState, settings: AppSettings, layout: HudSceneLayout,
    alerts: List<FlightAlert>, model: AircraftAlertModel?, thermal: EngineThermalObservation?,
    mapEndpoint: String?, sharedMap: kotlinx.coroutines.flow.StateFlow<MapConnection>?, connectionLabel: String?, messages: HudMessageState? = null) {
    val density = LocalDensity.current
    val flight = connection as? ConnectionState.Flying
    BoxWithConstraints(Modifier.fillMaxSize().testTag("hud-scene")) {
        val scale = minOf(maxWidth.value / layout.width, maxHeight.value / layout.height,
            if (layout.displayId == null) 1f else Float.MAX_VALUE).coerceAtLeast(0.01f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            layout.regions.forEach { region -> key(region.id) {
                val fields = region.fields ?: if (region.content == HudRegionContent.ENGINE) settings.hudEngineFields else settings.hudFields
                val scroll = key(flight != null, flight?.telemetry?.aircraft, region.content, region.engineIndex, fields) {
                    rememberScrollState()
                }
                if (region.visible && (region.content != HudRegionContent.ALERTS || alerts.isNotEmpty())) {
                    Box(Modifier.offset(region.x.dp, region.y.dp).size(region.width.dp, region.height.dp)
                        .clipToBounds().testTag("hud-region-${region.id}")
                        .background(Color(0xFF111820).copy(alpha = region.backgroundAlpha))) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = region.contentAlpha }.padding(12.dp)) {
                        Column(Modifier.fillMaxSize().padding(end = 8.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (region.content == HudRegionContent.ALERTS) FlightAlertPanel(alerts, compact = true,
                                maximumHeight = (region.height - 24).dp)
                            else if (flight == null) Text(connectionLabel ?: statusText(connection))
                            else when (region.content) {
                                HudRegionContent.FLIGHT -> {
                                    Text(connectionLabel ?: statusText(connection))
                                    FlightPanel(flight, compact = true, fields = HudField.selected(region.fields ?: settings.hudFields),
                                        mechanization = false, model = model, thermal = thermal, readingAlerts = alerts,
                                        aoaBarWarningPercent = settings.hudAoaBarWarningPercent, aoaWarningPercent = settings.hudAoaWarningPercent,
                                        compassHeadingUp = settings.hudCompassHeadingUp, hiddenLabels = settings.hudHiddenLabels,
                                        altitudeMode = settings.hudAltitudeMode, pollingIntervalMs = settings.pollIntervalMs)
                                    if (HudField.HEADING.id in (region.fields ?: settings.hudFields) && mapEndpoint != null) HudMapGrid(mapEndpoint, sharedMap)
                                }
                                HudRegionContent.ENGINE -> HudEnginePanel(flight.telemetry.engines, region.engineIndex,
                                    fields = HudEngineField.selected(region.fields ?: settings.hudEngineFields),
                                    warnings = engineReadingWarnings(flight, region.engineIndex, model, alerts, thermal))
                                HudRegionContent.ATTITUDE -> AttitudePanel(flight.telemetry, compact = true, model = model,
                                    earthFixed = settings.hudAttitudeEarthFixed, showAoaLimits = settings.hudAttitudeAoaLimits)
                                HudRegionContent.MECHANIZATION -> MechanizationPanel(flight.telemetry, model,
                                    settings.hudGear, settings.hudFlaps, settings.hudAirbrake, automaticSweep = true,
                                    alerts = alerts, showFlapBar = settings.hudFlapBar)
                                HudRegionContent.ALERTS -> Unit
                                HudRegionContent.MESSAGES -> HudRecentMessages(messages)
                            }
                        }
                        HudScrollIndicator(scroll, Modifier.matchParentSize())
                        }
                    }
                }
            } }
        }
        if (flight != null && settings.hudCrosshair) {
            if (settings.hudCrosshairImage.isEmpty()) CrosshairPanel(settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairRight)
            else ImageCrosshair(settings.hudCrosshairImage, settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairStretch, settings.hudCrosshairRight)
        }
    }
}
