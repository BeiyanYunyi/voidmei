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
    val hasCrosshairRegions = layout.regions.any { it.content == HudRegionContent.CROSSHAIR }
    val needsCrosshair = if (hasCrosshairRegions) layout.regions.any { it.visible && it.content == HudRegionContent.CROSSHAIR }
        else settings.hudCrosshair
    val crosshairImage = if (flight != null && needsCrosshair && settings.hudCrosshairImage.isNotEmpty())
        rememberCrosshairImage(settings.hudCrosshairImage) else null
    BoxWithConstraints(Modifier.fillMaxSize().testTag("hud-scene")) {
        val scale = minOf(maxWidth.value / layout.width, maxHeight.value / layout.height,
            if (layout.displayId == null) 1f else Float.MAX_VALUE).coerceAtLeast(0.01f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            layout.regions.forEach { region -> key(region.id) {
                val labelFont = remember(region.readingLabelFont) { region.readingLabelFont?.let { resolveTextFont(it).family } }
                val numberFont = remember(region.readingNumberFont) { region.readingNumberFont?.let { resolveHudNumberFont(it).family } }
                CompositionLocalProvider(LocalReadingTextWeights provides region.readingTextWeights, LocalReadingTextSizes provides region.readingTextSizes, LocalReadingLabelFont provides labelFont,
                    LocalReadingNumberFont provides (numberFont ?: LocalReadingNumberFont.current),
                    LocalReadingColumns provides (region.readingColumns ?: settings.hudReadingColumns),
                    LocalDensity provides Density(density.density * scale,
                        density.fontScale * ((region.fontScale ?: settings.hudFontScale) / settings.hudFontScale))) {
                val fields = region.fields ?: when (region.content) {
                    HudRegionContent.ENGINE -> settings.hudEngineFields
                    HudRegionContent.MECHANIZATION -> HudMechanizationField.inherited(settings)
                    HudRegionContent.MESSAGES -> HudMessageKind.entries.map { it.name.lowercase() }
                    HudRegionContent.CONTROLS -> listOf("aileron", "elevator", "rudder")
                    HudRegionContent.ALERTS -> AlertSeverity.entries.map { it.name.lowercase() }
                    else -> settings.hudFields
                }
                val scroll = key(flight != null, flight?.telemetry?.aircraft, region.content, region.engineIndex, fields, region.messageLimit, region.messageMaxLines, region.hiddenLabels, settings.hudHiddenLabels,
                    region.readingLabelFont, region.readingNumberFont, region.readingTextSizes, region.readingTextWeights,
                    region.readingColumns ?: settings.hudReadingColumns, region.fontScale ?: settings.hudFontScale,
                    region.width, region.height, region.showFlightInstruments, region.showFlightStatus, region.showEngineInstruments, region.showEngineReadings, region.showAircraftFuel, region.engineControlDimensions, region.engineControlsLayout, region.showControlStick) {
                    rememberScrollState()
                }
                val regionAlerts = if (region.content == HudRegionContent.ALERTS)
                    alerts.filter { it.severity.name.lowercase() in fields } else alerts
                if (region.visible && (flight == null || region.content != HudRegionContent.ALERTS || regionAlerts.isNotEmpty())) {
                    Box(Modifier.offset(region.x.dp, region.y.dp).size(region.width.dp, region.height.dp)
                        .clipToBounds().testTag("hud-region-${region.id}")
                        .background(Color(0xFF111820).copy(alpha = region.backgroundAlpha))
                        .hudRegionBorder(region.borderEnabled, region.borderAlpha)) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = region.contentAlpha }.padding(12.dp)) {
                        if (flight != null && region.content == HudRegionContent.CROSSHAIR) {
                            val size = minOf(region.width, region.height)
                            if (settings.hudCrosshairImage.isEmpty()) CrosshairPanel(size, Modifier.fillMaxSize())
                            else ImageCrosshair(settings.hudCrosshairImage, size, Modifier.fillMaxSize(), settings.hudCrosshairStretch, shared = crosshairImage)
                            if (region.title.isNotBlank()) Text(region.title, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                        } else if (flight != null && region.content == HudRegionContent.ALERTS) {
                            HudAlertRegion(region.title, regionAlerts)
                        } else if (flight != null && region.content == HudRegionContent.MAP) {
                            HudMapObjects(mapEndpoint, sharedMap, region.title)
                        } else if (flight != null && region.content == HudRegionContent.ATTITUDE) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (region.title.isNotBlank()) Text(region.title, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Box(Modifier.fillMaxWidth().weight(1f)) {
                                    AttitudePanel(flight.telemetry, compact = true, model = model,
                                        earthFixed = settings.hudAttitudeEarthFixed, showNorthPointer = settings.hudAttitudeNorthPointer, refreshMs = settings.hudAttitudeRefreshMs, showAoaLimits = settings.hudAttitudeAoaLimits,
                                        fillAvailable = true)
                                }
                            }
                        } else if (flight != null && region.content == HudRegionContent.COMPASS) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (region.title.isNotBlank()) Text(region.title, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                val heading = AttitudeGeometry.heading(flight.telemetry.headingDeg)
                                if (heading == null) Text("航向未知")
                                else {
                                    Text("航向 ${(kotlin.math.round(heading).toInt() % 360).toString().padStart(3, '0')}°")
                                    CompassPanel(heading, settings.hudCompassHeadingUp, Modifier.fillMaxWidth().weight(1f))
                                }
                            }
                        } else {
                        Column(Modifier.fillMaxSize().padding(end = 8.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (region.title.isNotBlank()) Text(region.title)
                            if (flight == null) Text(connectionLabel ?: statusText(connection))
                            else when (region.content) {
                                HudRegionContent.FLIGHT -> {
                                    if (region.showFlightStatus) Text(connectionLabel ?: statusText(connection))
                                    if (HudField.selected(fields).isEmpty()) Text("未选择飞行读数")
                                    FlightPanel(flight, compact = true, fields = HudField.selected(region.fields ?: settings.hudFields),
                                        mechanization = false, model = model, thermal = thermal, readingAlerts = alerts,
                                        aoaBarWarningPercent = settings.hudAoaBarWarningPercent, aoaWarningPercent = settings.hudAoaWarningPercent,
                                        compassHeadingUp = settings.hudCompassHeadingUp, hiddenLabels = region.hiddenLabels ?: settings.hudHiddenLabels,
                                        altitudeMode = settings.hudAltitudeMode, pollingIntervalMs = settings.pollIntervalMs,
                                        showInstruments = region.showFlightInstruments)
                                    if (HudField.HEADING.id in (region.fields ?: settings.hudFields) && mapEndpoint != null) HudMapGrid(mapEndpoint, sharedMap)
                                }
                                HudRegionContent.ENGINE -> {
                                    CompositionLocalProvider(LocalEngineControlDimensions provides region.engineControlDimensions) {
                                    HudEnginePanel(flight.telemetry.engines, region.engineIndex,
                                    fields = HudEngineField.selected(region.fields ?: settings.hudEngineFields),
                                    warnings = engineReadingWarnings(flight, region.engineIndex, model, alerts, thermal),
                                    showInstruments = region.showEngineInstruments, showReadings = region.showEngineReadings, controlsLayout = region.engineControlsLayout,
                                    heatBudget = thermal?.hudBudget(flight, model, region.engineIndex),
                                    powerPercent = flight.enginePowerPercentReading(region.engineIndex, model), tasKmh = flight.telemetry.tasKmh, hiddenLabels = region.hiddenLabels.orEmpty())
                                    if (region.showAircraftFuel) EngineAircraftFuelPanel(flight, alerts, region.showEngineInstruments)
                                    if (region.showEngineInstruments) CompressorStageBar(flight, region.engineIndex, model, fields)
                                    HudCompressorAdvice(flight, region.engineIndex, model, fields)
                                    }
                                }
                                HudRegionContent.ATTITUDE -> Unit
                                HudRegionContent.MECHANIZATION -> {
                                    if (HudMechanizationField.entries.none { it.id in fields }) Text("未选择机械化内容")
                                    MechanizationPanel(flight.telemetry, model,
                                        HudMechanizationField.GEAR.id in fields, HudMechanizationField.FLAPS.id in fields,
                                        HudMechanizationField.AIRBRAKE.id in fields, automaticSweep = true,
                                        alerts = alerts, showFlapBar = HudMechanizationField.FLAP_BAR.id in fields)
                                }
                                HudRegionContent.ALERTS -> Unit
                                HudRegionContent.MESSAGES -> HudRecentMessages(messages,
                                    HudMessageKind.entries.filter { it.name.lowercase() in fields }.toSet(), region.messageLimit, region.messageMaxLines)
                                HudRegionContent.MAP -> Unit
                                HudRegionContent.CROSSHAIR -> Unit
                                HudRegionContent.COMPASS -> Unit
                                HudRegionContent.CONTROLS -> ControlSurfacePanel(flight.telemetry, region.fields, region.showControlStick)
                            }
                        }
                        HudScrollIndicator(scroll, Modifier.matchParentSize())
                        }
                        }
                        if (LocalHudLayoutInspection.current && scroll.maxValue in 1 until Int.MAX_VALUE) {
                            Text("内容超出区域", Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                                .testTag("hud-region-overflow-${region.id}")
                                .background(Color(0xFF713E00)).padding(horizontal = 4.dp, vertical = 2.dp),
                                color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } } }
        }
        if (flight != null && settings.hudCrosshair && !hasCrosshairRegions) {
            if (settings.hudCrosshairImage.isEmpty()) CrosshairPanel(settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairRight)
            else ImageCrosshair(settings.hudCrosshairImage, settings.hudCrosshairSizeDp, Modifier.matchParentSize(), settings.hudCrosshairStretch, settings.hudCrosshairRight, crosshairImage)
        }
    }
}
