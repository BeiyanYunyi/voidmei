package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import voidmei.config.AppSettings
import voidmei.config.WindowPosition as SavedPosition
import voidmei.telemetry.*
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
fun main(args: Array<String>) {
    val defaults = SettingsStore.desktopDefaults()
    val store = SettingsStore(SettingsStore.defaultPath(), defaults)
    val loaded = store.load()
    configureStartupRenderer(loaded.settings.softwareRendering)
    application {
        DisposableEffect(Unit) {
            val heartbeat = if (java.lang.Boolean.getBoolean("voidmei.diagnostics.uiHeartbeat")) startUiHeartbeat() else null
            onDispose { heartbeat?.close() }
        }
        var settings by remember { mutableStateOf(loaded.settings.withStartupHudOptions(args)) }
        val typography = remember(settings.textFont) { textTypography(resolveTextFont(settings.textFont).family) }
        var settingsError by remember { mutableStateOf(loaded.error) }
        var hudPointerError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val voicePlayer = remember { VoicePlayer() }
        val alertEvaluator = remember { FlightAlerts() }
        val thermalMonitor = remember { EngineThermalMonitor() }
        var thermalObservation by remember { mutableStateOf<EngineThermalObservation?>(null) }
        var alerts by remember { mutableStateOf<List<FlightAlert>>(emptyList()) }
        var voiceError by remember { mutableStateOf<String?>(null) }
        suspend fun stopVoice(includePreview: Boolean = true) {
            try { withContext(Dispatchers.IO) { if (includePreview) voicePlayer.stop() else voicePlayer.stopAutomatic() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { voiceError = "语音停止失败：${e.message}" }
        }
        DisposableEffect(voicePlayer) { onDispose {
            try { voicePlayer.close() }
            catch (e: Exception) { println("[VoidMei voice] 释放失败：${e.message}") }
        } }
        LaunchedEffect(settings.voiceEnabled) { if (!settings.voiceEnabled) stopVoice() }
        LaunchedEffect(settings.voiceDirectory, settings.voicePack, settings.alertVoices) {
            stopVoice()
        }
        LaunchedEffect(settings.voiceVolume) {
            try { withContext(Dispatchers.IO) { voicePlayer.setVolume(settings.voiceVolume) }; voiceError = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { voiceError = "语音不可用：${e.message}" }
        }
        val recorder = remember { FlightRecorder(scope) }
        val recording by recorder.state.collectAsState()
        val lastRecording by recorder.lastRecording.collectAsState()
        var recordingPath by remember { mutableStateOf(settings.recordingDirectory) }
        var recordingError by remember { mutableStateOf<String?>(null) }
        var recordingBusy by remember { mutableStateOf(false) }
        val writer = remember { if (loaded.error == null) SettingsWriter(scope, store) { settingsError = it } else null }
        var closing by remember { mutableStateOf(false) }
        var mainVisible by remember { mutableStateOf(true) }
        var mainRestoreRequest by remember { mutableStateOf(0L) }
        var trayAvailable by remember { mutableStateOf(false) }
        var desktopTray by remember { mutableStateOf<DesktopTray?>(null) }
        var trayError by remember { mutableStateOf<String?>(null) }
        var recordingExitFailure by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        LaunchedEffect(Unit) {
            if (loaded.error == null && loaded.settings.recordingAutoStart && !closing) {
                recordingBusy = true
                try { startConfiguredRecording(recorder, loaded.settings) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { recordingError = e.message ?: "无法自动开启记录" }
                finally { recordingBusy = false }
            }
        }
        var offlineModels by remember { mutableStateOf(false) }
        var hotkeyError by remember { mutableStateOf<String?>(null) }
        var hotkeyActive by remember { mutableStateOf(false) }
        LaunchedEffect(settings.hudHotkeyEnabled || settings.modelWindowHotkeyEnabled) {
            hotkeyError = null
            if (settings.hudHotkeyEnabled || settings.modelWindowHotkeyEnabled) {
                val session = HudHotkey(modelToggle = {
                    launch {
                        if (settings.modelWindowHotkeyEnabled && !closing) settings = settings.copy(modelWindowEnabled = !settings.modelWindowEnabled)
                    }
                }) {
                    launch {
                        if (settings.hudHotkeyEnabled && !closing) settings = settings.copy(hudEnabled = !settings.hudEnabled)
                    }
                }
                try {
                    withContext(Dispatchers.IO) { session.start() }
                    hotkeyActive = true
                    awaitCancellation()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { hotkeyError = "热键不可用：${e.message}" }
                catch (e: LinkageError) { hotkeyError = "热键库加载失败：${e.message}" }
                finally {
                    hotkeyActive = false
                    withContext(NonCancellable + Dispatchers.IO) {
                        try { session.close() }
                        catch (e: Exception) { withContext(Dispatchers.Main) { hotkeyError = "热键释放失败：${e.message}" } }
                    }
                }
            }
        }
        val mainState = rememberWindowState(position = restorePosition(settings.mainPosition), width = 940.dp, height = 760.dp)
        val hudState = rememberWindowState(position = restorePosition(settings.hudPosition), width = settings.hudWidthDp.dp, height = 510.dp)
        val modelWindowState = rememberWindowState(position = restorePosition(settings.modelWindowPosition), width = 760.dp, height = 680.dp)
        var hudContentHeight by remember { mutableStateOf(510.dp) }
        fun finalSettings() = settings.copy(mainPosition = mainState.savedPosition() ?: settings.mainPosition,
            hudPosition = hudState.savedPosition() ?: settings.hudPosition,
            modelWindowPosition = modelWindowState.savedPosition() ?: settings.modelWindowPosition)
        fun closeApp(saveSettings: Boolean = true, acknowledgeRecordingFailure: Boolean = false) {
            if (!closing && recordingExitFailure == null) {
                closing = true
                scope.launch {
                    val recordingFailure = stopRecordingForExit(recorder)
                    if (recordingFailure != null && !acknowledgeRecordingFailure) {
                        recordingExitFailure = saveSettings to recordingFailure
                        closing = false
                        return@launch
                    }
                    if (!saveSettings) writer?.discard()
                    if (!saveSettings || writer?.close(finalSettings()) != false) {
                        recorder.close()
                        exitApplication()
                    } else closing = false
                }
            }
        }
        val trayShow by rememberUpdatedState<() -> Unit> { mainVisible = true; mainState.isMinimized = false; mainRestoreRequest++ }
        val trayHud by rememberUpdatedState<() -> Unit> { if (!closing) settings = settings.copy(hudEnabled = !settings.hudEnabled) }
        val trayExit by rememberUpdatedState<() -> Unit> { trayShow(); closeApp() }
        DisposableEffect(Unit) {
            val tray = try { installDesktopTray({ trayShow() }, { trayHud() }, { trayExit() }) { available ->
                trayAvailable = available
                trayError = if (available) null else "桌面托盘已断开，已恢复主窗口。"
                if (!available) trayShow()
            } }
                catch (e: Exception) { trayError = e.message ?: "托盘创建失败"; null }
            desktopTray = tray
            trayAvailable = tray != null
            mainVisible = !shouldStartInTray(settings.startInTray, trayAvailable, "--no-hud" in args, loaded.error != null)
            onDispose { desktopTray = null; tray?.close() }
        }
        LaunchedEffect(recorder) {
            recorder.performance.collect { observation ->
                if (settings.recordingPerformanceNotifications && trayAvailable)
                    desktopTray?.showMessage("飞行采样记录", performanceMessage(observation))
            }
        }
        RecordingNotificationEffect(recording, lastRecording) { notice ->
            if (trayAvailable) desktopTray?.showMessage(notice.title, notice.message)
        }
        RecordingFailureEffect(recording, recordingError) { trayShow() }
        LaunchedEffect(settingsError, recordingExitFailure) {
            if (settingsError != null || recordingExitFailure != null) trayShow()
        }
        LaunchedEffect(Unit) {
            snapshotFlow { settings }.debounce(350).collect { writer?.update(it) }
        }
        LaunchedEffect(mainState, hudState) {
            snapshotFlow { mainState.savedPosition() to hudState.savedPosition() }.collect { (main, hud) ->
                settings = settings.copy(mainPosition = main ?: settings.mainPosition, hudPosition = hud ?: settings.hudPosition)
            }
        }
        LaunchedEffect(modelWindowState) {
            snapshotFlow { modelWindowState.savedPosition() }.collect { position ->
                if (position != null) settings = settings.copy(modelWindowPosition = position)
            }
        }
        var endpoint by remember { mutableStateOf(System.getenv("VOIDMEI_ENDPOINT") ?: settings.endpoint) }
        var activeEndpoint by remember { mutableStateOf(endpoint) }
        var generation by remember { mutableStateOf(0) }
        val overlay = settings.hudEnabled
        val gameFocus by rememberGameFocus(overlay && settings.hudAutoHideOnFocusLoss)
        var endpointError by remember { mutableStateOf<String?>(null) }
        var connection by remember { mutableStateOf<ConnectionState>(ConnectionState.Connecting) }
        var messagePanelExpanded by remember { mutableStateOf(false) }
        val hudNeedsMessages = settings.needsHudMessages()
        val messageSession = rememberHudMessageSession(activeEndpoint, connection, hudNeedsMessages || messagePanelExpanded, generation)

        val sharedMap = rememberTelemetryMapSession(activeEndpoint, connection, generation)

        ConnectionNotificationEffect(connection, activeEndpoint, generation) { title, message ->
            if (settings.connectionNotifications && trayAvailable) desktopTray?.showMessage(title, message)
        }
        val flightModel = rememberTelemetryFlightModelSession(connection, settings.fmDataRoot, activeEndpoint to generation)
        val modelForAlerts = flightModel.alertModel
        val currentAlertModel by rememberUpdatedState(modelForAlerts)
        LaunchedEffect(activeEndpoint, generation) {
            val arrivalCue = FlightArrivalCue()
            val engineResponse = EngineResponseMonitor()
            val wepFuel = WepFuelMonitor()
            suspend fun process(state: ConnectionState): TelemetryDisplay {
                val alertSettings = settings
                val model = currentAlertModel
                val nowMs = System.nanoTime() / 1_000_000
                val arrived = arrivalCue.update(state, nowMs, alertSettings.voiceEnabled && alertSettings.voiceVolume > 0 &&
                    alertSettings.alertVoices[FlightAlert.CONNECTION_READY.voice]?.enabled == true)
                val thermal = thermalMonitor.update(state, model, nowMs)
                val update = alertEvaluator.updateForAircraft(state, model,
                    nowMs, alertSettings.voiceEnabled && alertSettings.voiceVolume > 0,
                    alertSettings.alertVoices.filterValues { !it.enabled }.keys, thermalObservation = thermal,
                    voiceAvailable = voicePlayer::canPlay)
                if (state !is ConnectionState.Flying) stopVoice(includePreview = false)
                val sound = update.voice ?: FlightAlert.CONNECTION_READY.takeIf {
                    arrived && update.active.isEmpty() && voicePlayer.canPlay(it)
                }
                sound?.let { alert ->
                    try {
                        voicePlayer.play(alert, alertSettings.voiceVolume, VoiceResources(java.nio.file.Path.of(alertSettings.voiceDirectory),
                            alertSettings.alertVoices[alert.voice]?.pack ?: alertSettings.voicePack))
                        voiceError = null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        update.voiceAttemptId?.let(alertEvaluator::voiceFailed)
                        voiceError = "语音不可用：${e.message}"
                    }
                }
                return TelemetryDisplay(state, thermal, update.active)
            }
            fun display(update: TelemetryDisplay) {
                connection = update.connection
                thermalObservation = update.thermal
                alerts = update.alerts
            }
            recorder.record(ConnectionState.Connecting)
            display(withContext(Dispatchers.Default) { process(ConnectionState.Connecting) })
            try {
                HttpTelemetryTransport(activeEndpoint).use { transport ->
                    TelemetryPoller(transport, intervalProvider = { settings.pollIntervalMs }).states()
                        .processBeforeDisplay {
                            val model = currentAlertModel
                            val nowMs = System.nanoTime() / 1_000_000
                            val enriched = wepFuel.update(engineResponse.update(it, model, nowMs), model, nowMs)
                            recorder.record(enriched); process(enriched)
                        }.collect { display(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                display(withContext(Dispatchers.Default) { process(ConnectionState.Disconnected(e.message ?: "Connection failed")) })
            } finally {
                withContext(NonCancellable) { stopVoice() }
            }
        }

        RestorableDesktopWindow(onCloseRequest = { closeApp() }, title = "VoidMei · Kotlin",
            state = mainState, visible = mainVisible, restoreRequest = mainRestoreRequest) {
            val renderer = rememberRendererDiagnostics(window)
            var showRenderer by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalReadingNumberFont provides remember(settings.numberFont) { resolveHudNumberFont(settings.numberFont).family },
                LocalReadingColors provides readingColors(settings)) {
                MaterialTheme(typography = typography, colorScheme = darkColorScheme(primary = Color(0xFF84DEC6), background = Color(0xFF111820))) {
                    recordingExitFailure?.let { (saveSettings, reason) ->
                        RecordingExitDialog(reason, onReturn = { recordingExitFailure = null }, onExit = {
                            recordingExitFailure = null
                            closeApp(saveSettings, acknowledgeRecordingFailure = true)
                        })
                    }
                    Surface(Modifier.fillMaxSize()) {
                        SectionPage { anchors ->
                            SectionHeading(MainSection.SETTINGS, anchors)
                            Text("VOIDMEI", style = MaterialTheme.typography.headlineLarge)
                            Text("飞行遥测 · Kotlin Multiplatform", color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { showRenderer = !showRenderer }) { Text("渲染信息") }
                            if (showRenderer) {
                                Text(renderer, style = MaterialTheme.typography.bodySmall)
                                SoftwareRenderingSettings(settings.softwareRendering) { settings = settings.copy(softwareRendering = it) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(endpoint, { endpoint = it }, Modifier.weight(1f), label = { Text("遥测服务器") }, singleLine = true)
                                Button(onClick = {
                                    try {
                                        validateEndpoint(endpoint)
                                        endpointError = null
                                        activeEndpoint = endpoint
                                        settings = settings.copy(endpoint = endpoint)
                                        generation++
                                    } catch (e: Exception) { endpointError = e.message }
                                }) { Text("连接") }
                                FilledTonalButton(onClick = { settings = settings.copy(hudEnabled = !overlay) }) { Text(if (overlay) "关闭 HUD" else "打开 HUD") }
                            }
                            endpointError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Switch(settings.hudHotkeyEnabled, { settings = settings.copy(hudHotkeyEnabled = it) })
                                Text("Ctrl + Shift + H 切换 HUD" + if (hotkeyActive && settings.hudHotkeyEnabled) " · 已启用" else "")
                            }
                            hotkeyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(settings.startInTray, { settings = settings.copy(startInTray = it) }, enabled = !closing)
                                Text("下次启动进入托盘")
                                if (trayAvailable) TextButton(onClick = { mainVisible = false }) { Text("隐藏到托盘") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(settings.connectionNotifications, { settings = settings.copy(connectionNotifications = it) }, enabled = !closing)
                                Text("通知 8111 连接状态变化（需要托盘）")
                            }
                            if (!trayAvailable) Text(trayError?.let { "托盘不可用：$it" } ?: "当前桌面不支持托盘，启动时保持主窗口可见。")
                            settingsError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                                TextButton(enabled = !closing, onClick = { closeApp(saveSettings = false) }) { Text("不保存并退出") }
                            }
                            TextFontSettings(settings.textFont) { settings = settings.copy(textFont = it) }
                            ReadingColorSettings(settings) { settings = it }
                            NumberFontSettings(settings.numberFont) { settings = settings.copy(numberFont = it) }
                            PollingIntervalSettings(settings.pollIntervalMs) { settings = settings.copy(pollIntervalMs = it) }
                            Text("HUD 背景不透明度 ${(settings.hudOpacity * 100).toInt()}%")
                            Slider(value = settings.hudOpacity, onValueChange = { settings = settings.copy(hudOpacity = it) }, valueRange = 0f..1f)
                            TextButton(onClick = {
                                mainState.position = resetWindowPosition()
                                hudState.position = resetWindowPosition(64)
                                modelWindowState.position = resetWindowPosition(96)
                                settings = settings.copy(mainPosition = null, hudPosition = null, modelWindowPosition = null)
                            }) { Text("重置窗口位置") }
                            ResetSettingsPanel(defaults, enabled = !closing && !recordingBusy && writer != null && settingsError == null,
                                recording = recording is RecordingState.Active) { restored ->
                                settings = restored
                                endpoint = restored.endpoint
                                endpointError = null
                                recordingPath = restored.recordingDirectory
                                mainState.position = resetWindowPosition()
                                hudState.position = resetWindowPosition(64)
                                modelWindowState.position = resetWindowPosition(96)
                            }
                            SettingsTransferPanel(finalSettings(),
                                canRestore = !closing && !recordingBusy && recording !is RecordingState.Active && writer != null && settingsError == null,
                                onRestore = { restored ->
                                    settings = restored
                                    endpoint = restored.endpoint
                                    endpointError = null
                                    recordingPath = restored.recordingDirectory
                                    mainState.position = restorePosition(restored.mainPosition)
                                    hudState.position = restorePosition(restored.hudPosition)
                                    modelWindowState.position = restorePosition(restored.modelWindowPosition)
                                })
                            LegacySettingsImport(settings) { updated ->
                                if (updated.endpoint != settings.endpoint) {
                                    validateEndpoint(updated.endpoint)
                                    endpoint = updated.endpoint
                                }
                                settings = updated
                            }
                            SectionHeading(MainSection.HUD, anchors)
                            HudSettingsPanel(settings) {
                                settings = it
                                if (!it.hudClickThrough) hudPointerError = null
                            }
                            hudPointerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (overlay && settings.hudAutoHideOnFocusLoss) Text(when (gameFocus) {
                                GameFocus.GAME -> "游戏在前台，HUD 显示。"
                                GameFocus.OTHER -> "已切出游戏，HUD 暂时隐藏；遥测与录制继续。"
                                GameFocus.UNKNOWN -> "无法确认前台窗口，HUD 保持显示。"
                            }, style = MaterialTheme.typography.bodySmall)
                            SectionHeading(MainSection.FLIGHT, anchors)
                            Text(statusText(connection), color = MaterialTheme.colorScheme.primary)
                            val flight = connection as? ConnectionState.Flying
                            if (flight != null) {
                                FlightPanel(flight, model = modelForAlerts, thermal = thermalObservation)
                                AttitudePanel(flight.telemetry, model = modelForAlerts)
                                Text("发动机", style = MaterialTheme.typography.titleLarge)
                                Text("燃油压力仪表（原值） ${flight.telemetry.fuelPressureRaw.display()}")
                                Text("滑油压力仪表（原值） ${flight.telemetry.oilPressureRaw.display()}（单位及发动机归属未确定）")
                                if (flight.telemetry.engines.size > 1 && flight.telemetry.fuelPressureRaw != null)
                                    Text("压力仪表未标明引擎编号，多引擎低压判定暂不可用。", style = MaterialTheme.typography.bodySmall)
                                val compressorAdvice = CompressorAdvice.recommendations(flight.telemetry,
                                    modelForAlerts?.parametersFor(flight.telemetry.aircraft)?.engineCompressors.orEmpty())
                                val compressorFuel = modelForAlerts?.parametersFor(flight.telemetry.aircraft)?.compressorFuel
                                val compressorFuelLabel = compressorFuel?.let { fuelName(it.id) } ?: "基础燃油"
                                compressorAdvice.forEach { advice ->
                                    Text("#${advice.engineIndex} 增压器 ${advice.actualStage} → ${advice.recommendedStage}（$compressorFuelLabel、15°C 模型估算）")
                                }
                                val negativeLoadEngines = EngineWarnings.lowThrustUnderNegativeLoad(flight.telemetry)
                                val highRpmEngines = EngineWarnings.highRpm(flight.telemetry,
                                    modelForAlerts?.parametersFor(flight.telemetry.aircraft)?.engineRpmLimits.orEmpty())
                                val lowRpmEngines = EngineWarnings.lowRpm(flight.telemetry,
                                    modelForAlerts?.parametersFor(flight.telemetry.aircraft)?.engineRpmReferences.orEmpty())
                                flight.telemetry.engines.forEach { engine ->
                                    if (engine.index in lowRpmEngines)
                                        Text("#${engine.index} 转速相对油门偏低（模型基准）", color = MaterialTheme.colorScheme.error)
                                    if (engine.index in highRpmEngines)
                                        Text("#${engine.index} 达到模型最高允许转速", color = MaterialTheme.colorScheme.error)
                                    if (engine.index in negativeLoadEngines)
                                        Text("#${engine.index} 负过载、大油门时推力低于 50 kgf", color = MaterialTheme.colorScheme.error)
                                    HudEnginePanel(listOf(engine), engine.index, compact = false)
                                }
                                EngineThermalBudgetPanel(thermalObservation?.budgetsFor(flight, modelForAlerts).orEmpty())
                                val t = flight.telemetry
                                Text("控制面", style = MaterialTheme.typography.titleLarge)
                                Text("侧滑角 ${t.sideslipAngleDeg.display()}°   滚转角速度 ${t.rollRateDegPerSecond.display()} °/s")
                                Text("副翼 ${t.aileronPercent.display()} %   升降舵 ${t.elevatorPercent.display()} %   方向舵 ${t.rudderPercent.display()} %")
                                FlightAnalysisPanel(flight.metrics)
                            } else {
                                Text("启动战争雷霆试飞或进入战斗后，数据会自动显示。")
                            }
                            SectionHeading(MainSection.VOICE, anchors)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("语音告警")
                                Switch(checked = settings.voiceEnabled, onCheckedChange = { settings = settings.copy(voiceEnabled = it) })
                            }
                            Text("语音音量 ${settings.voiceVolume} · 0 静音 / 100 原始 / 200 最大增益")
                            Slider(value = settings.voiceVolume.toFloat(),
                                onValueChange = { settings = settings.copy(voiceVolume = it.roundToInt()) }, valueRange = 0f..200f)
                            VoicePackPanel(settings, onSettings = { settings = it })
                            AlertVoicePanel(settings, onPreview = { alert ->
                                voicePlayer.play(alert, settings.voiceVolume, VoiceResources(java.nio.file.Path.of(settings.voiceDirectory),
                                    settings.alertVoices[alert.voice]?.pack ?: settings.voicePack), preview = true)
                            }, onStopPreview = {
                                scope.launch {
                                    try { withContext(Dispatchers.IO) { voicePlayer.stopPreview() } }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { voiceError = "试听停止失败：${e.message}" }
                                }
                            }) { settings = it }
                            FlightAlertPanel(alerts)
                            voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            SectionHeading(MainSection.MODEL, anchors)
                            TextButton(enabled = !closing, onClick = { offlineModels = true }) { Text("打开离线模型查看") }
                            ModelWindowControls(settings, enabled = !closing) { settings = it }
                            if (settings.modelWindowHotkeyEnabled) {
                                Text(if (hotkeyActive) "模型浮窗热键已启用" else "模型浮窗热键未就绪")
                                hotkeyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                            FlightModelPanel(flight?.telemetry, settings.fmDataRoot, onModel = { _, _ -> }, session = flightModel, hiddenSections = settings.hiddenModelSections,
                                onHiddenSections = { settings = settings.copy(hiddenModelSections = it) }, onDataRoot = {
                                settings = settings.copy(fmDataRoot = it)
                            })
                            SectionHeading(MainSection.RECORDS, anchors)
                            Text("飞行记录", style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(settings.recordingAutoStart, { settings = settings.copy(recordingAutoStart = it) }, enabled = !closing)
                                Text("启动时自动开启记录（下次启动生效）")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(recordingPath, { recordingPath = it }, Modifier.weight(1f),
                                    label = { Text("CSV 保存目录") }, enabled = recording !is RecordingState.Active && !recordingBusy && !closing, singleLine = true)
                                Button(enabled = !closing && !recordingBusy && recordingPath.isNotBlank(), onClick = {
                                    recordingBusy = true
                                    scope.launch {
                                        try {
                                            recordingError = null
                                            if (recording is RecordingState.Active) recorder.stop() else {
                                                recorder.start(java.nio.file.Path.of(recordingPath))
                                                if (recorder.state.value is RecordingState.Active) settings = settings.copy(recordingDirectory = recordingPath)
                                            }
                                        } catch (e: Exception) {
                                            currentCoroutineContext().ensureActive()
                                            recordingError = e.message ?: "无法更改录制状态"
                                        } finally { recordingBusy = false }
                                    }
                                }) { Text(if (recording is RecordingState.Active) "停止记录" else "开始记录") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Switch(settings.recordingPerformanceNotifications,
                                    { settings = settings.copy(recordingPerformanceNotifications = it) }, enabled = !closing)
                                Text("通知高度档及机动采样结果（需要托盘）")
                            }
                            RecordingDirectorySave(settings.recordingDirectory, recordingPath,
                                enabled = recording !is RecordingState.Active && !recordingBusy && !closing) { path ->
                                recordingPath = path
                                settings = settings.copy(recordingDirectory = path)
                            }
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(when (val current = recording) {
                                RecordingState.Stopped -> "未记录"
                                is RecordingState.Active -> current.file?.let { "已写入 ${current.samples} 帧：$it" } ?: "已开启记录，等待飞行数据"
                                is RecordingState.Failed -> "记录失败：${current.description()}"
                            }) }
                            recordingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            RecordingAnalysisPanel(latestFile = lastRecording?.flight?.toString())
                            EngineRecordingPanel(latestFile = lastRecording?.engines?.toString())
                            SectionHeading(MainSection.MAP, anchors)
                            MapPanel(activeEndpoint, connection is ConnectionState.Flying, sharedMap)
                            HudMessagesPanel(activeEndpoint, flight, messageSession) { messagePanelExpanded = it }
                            Text("迁移开发版：完整功能迁移、真实游戏及多平台验证仍在进行。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (settings.modelWindowEnabled && !closing) ModelFloatingWindow(modelWindowState, settings,
            (connection as? ConnectionState.Flying)?.telemetry, flightModel,
            onClose = { settings = settings.copy(modelWindowEnabled = false, modelWindowPosition = modelWindowState.savedPosition() ?: settings.modelWindowPosition) },
            onChange = { settings = it })

        if (offlineModels) Window(onCloseRequest = { offlineModels = false }, title = "VoidMei · 离线模型",
            state = rememberWindowState(width = 960.dp, height = 800.dp)) {
            rememberRendererDiagnostics(window)
            MaterialTheme(typography = typography, colorScheme = hudColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OfflineModelPanel(settings.fmDataRoot, settings.hiddenModelSections) { settings = settings.copy(hiddenModelSections = it) }
                    }
                }
            }
        }

        if (overlay) HudWindow(
            onCloseRequest = { settings = settings.copy(hudEnabled = false, hudPosition = hudState.savedPosition()) },
            state = hudState,
            compatibilityMode = settings.hudCompatibilityMode,
            clickThrough = settings.hudClickThrough || settings.hudSceneLayout?.enabled == true,
            onPointerError = { hudPointerError = it },
            visible = !settings.hudAutoHideOnFocusLoss || gameFocus != GameFocus.OTHER,
        ) {
            rememberRendererDiagnostics(window)
            if (!java.lang.Boolean.getBoolean("voidmei.diagnostics.hud.fixedSize"))
                updateHudWindowSize(window, hudState, settings.hudWidthDp, hudContentHeight, settings.hudSceneLayout?.takeIf { it.enabled })
            MaterialTheme(typography = typography, colorScheme = hudColorScheme()) {
                HudPanel(connection, settings, alerts, modelForAlerts, mapEndpoint = activeEndpoint, sharedMap = sharedMap, thermal = thermalObservation,
                    messages = messageSession.state, onContentHeightChanged = { hudContentHeight = it }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HudDraggableArea(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                                Text("VOIDMEI · 拖动移动", color = Color(0xFF84DEC6))
                            }
                        }
                        TextButton(onClick = { settings = settings.copy(hudEnabled = false, hudPosition = hudState.savedPosition()) }, contentPadding = PaddingValues(0.dp)) { Text("关闭") }
                    }
                }
            }
        }
    }

}

private fun WindowState.savedPosition(): SavedPosition? = (position as? WindowPosition.Absolute)?.let {
    SavedPosition(it.x.value, it.y.value)
}

@Composable
internal fun rememberRendererDiagnostics(window: java.awt.Window): String {
    var report by remember(window) { mutableStateOf("") }
    LaunchedEffect(window) {
        while (isActive) {
            delay(1000)
            val current = rendererDiagnostics(window)
            if (current != report) {
                report = current
                println("[${(window as? java.awt.Frame)?.title ?: "VoidMei"}] $current")
            }
        }
    }
    return report
}

@Composable
private fun FlightAnalysisPanel(metrics: FlightMetrics) {
    Text("飞行分析", style = MaterialTheme.typography.titleLarge)
    Text("加速度 ${metrics.accelerationMps2.display(2)} m/s²   转弯率估计 ${metrics.estimatedTurnRateDegps.display()} °/s   半径估计 ${metrics.estimatedTurnRadiusM.display(0)} m")
    Text("燃油 ${metrics.fuelPercent.display()} %   消耗 ${metrics.fuelConsumptionKgPerMinute.display()} kg/min   续航估计 ${metrics.fuelEnduranceSeconds?.div(60).display()} min")
    Text("总功率 ${metrics.totalPowerHp.display(0)} hp   总推力 ${metrics.totalThrustKgf.display(0)} kgf   推力功率 ${metrics.thrustPowerKw.display()} kW")
    Text("标准大气密度 ${metrics.standardDensityKgM3.display(3)} kg/m³   标准大气动压 ${metrics.standardDynamicPressurePa?.div(1000).display(2)} kPa")
    Text("续航采用最近 30 秒燃油变化，至少采样 10 秒；燃油泄漏或抛弃油箱也会计入。大气数据为标准模型估计。", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun FlightPanel(flight: ConnectionState.Flying, compact: Boolean = false, fields: List<HudField> = HudField.selected(HudField.defaults), mechanization: Boolean = true, model: AircraftAlertModel? = null, thermal: EngineThermalObservation? = null, showGear: Boolean = true, showFlaps: Boolean = true, showAirbrake: Boolean = true, aoaBarWarningPercent: Double = 25.0, aoaWarningPercent: Double = 20.0, readingAlerts: List<FlightAlert> = emptyList(), showFlapBar: Boolean = true, compassHeadingUp: Boolean = false, hiddenLabels: List<String> = emptyList(), altitudeMode: HudAltitudeMode = HudAltitudeMode.SEA_LEVEL, pollingIntervalMs: Long? = null, showInstruments: Boolean = true) {
    val t = flight.telemetry
    val altitude = if (compact) altitudeMode.reading(flight) else HudAltitudeMode.SEA_LEVEL.reading(flight)
    fun readingUnit(field: HudField) = if (field == HudField.ALTITUDE) altitude.unit else field.unitFor(t, flight.metrics, model)
    val rows = fields.map { field ->
        val value = if (field == HudField.ALTITUDE) altitude.metres else field.value(flight, model)
        field.label to if (field == HudField.HEAT_TOLERANCE) {
            val range = thermal?.hudBudget(flight, model)
            formatThermalBudget(range)
        } else if (field == HudField.WEP_TIME) formatFuelTimeUpperBound(value)
        else if (field == HudField.WEP_FUEL) "${roundUpperBound(value, 1).display(1)} kg"
        else if (field == HudField.ENDURANCE_CLOCK) formatFuelTime(value)
            else "${value.display(field.decimalsFor(flight.metrics))} ${readingUnit(field)}".trimEnd()
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp)) {
        val aoaMargin = if (compact && HudField.AOA in fields) PositiveAoaMargin.fromTelemetry(t, model) else null
        val warnAoa = aoaMargin != null && (aoaMargin.degrees <= 0 || aoaMargin.fraction < aoaWarningPercent / 100)
        val warnings = mutableMapOf<Int, String>()
        val engineWarnings = if (compact) engineReadingWarnings(flight, 1, model, readingAlerts, thermal) else emptyMap()
        if (compact && HudField.HEAT_TOLERANCE in fields && FlightAlert.ENGINE_OVERHEAT in readingAlerts &&
            thermal?.hudBudget(flight, model) != null && 1 in thermal.warningEngines(flight, model))
            warnings[fields.indexOf(HudField.HEAT_TOLERANCE)] = FlightAlert.ENGINE_OVERHEAT.label
        if (warnAoa) warnings[fields.indexOf(HudField.AOA)] = "模型迎角余量预警"
        if (compact) fields.forEachIndexed { index, field ->
            val engineField = when (field) {
                HudField.ENGINE1_RPM -> HudEngineField.RPM
                HudField.ENGINE1_THRUST -> HudEngineField.THRUST
                HudField.ENGINE_TEMPERATURE -> HudEngineField.WATER_TEMPERATURE.takeIf { t.displayTemperature(false)?.engineIndex == 1 }
                HudField.OIL_TEMPERATURE -> HudEngineField.OIL_TEMPERATURE.takeIf { t.displayTemperature(true)?.engineIndex == 1 }
                else -> null
            }
            engineWarnings[engineField]?.takeIf { field.value(flight, model) != null }?.let { warnings[index] = it }
            val alert = when (field) {
                HudField.IAS -> if (FlightAlert.IAS_LIMIT in readingAlerts) FlightAlert.IAS_LIMIT else FlightAlert.STALL_SPEED
                HudField.MACH -> FlightAlert.MACH_LIMIT
                HudField.LOAD -> FlightAlert.LOAD_LIMIT
                HudField.ALTITUDE -> if (altitude.radarEstimated) FlightAlert.TERRAIN_CLOSURE else FlightAlert.ALTITUDE_DESCENT
                HudField.RADIO_ALTITUDE_ESTIMATE -> FlightAlert.TERRAIN_CLOSURE
                HudField.CLIMB -> if (FlightAlert.HIGH_DESCENT in readingAlerts) FlightAlert.HIGH_DESCENT else FlightAlert.ALTITUDE_DESCENT
                HudField.FUEL_PRESSURE_RAW -> FlightAlert.LOW_FUEL_PRESSURE
                HudField.GEAR -> FlightAlert.GEAR_LIMIT.takeIf { t.gearPercent?.let { it > 0 && it <= 100 } == true }
                HudField.FLAPS -> FlightAlert.FLAP_LIMIT.takeIf { t.flapsPercent?.let { it > 0 && it <= 100 } == true }
                HudField.AIRBRAKE -> FlightAlert.AIRBRAKE_EXTENDED.takeIf {
                    t.airbrakePercent?.let { it in 90.0..100.0 } == true && t.gearPercent?.let { it >= 0 && it < 100 } == true }
                HudField.FUEL, HudField.FUEL_PERCENT -> fuelReadingWarning(readingAlerts)
                else -> null
            }
            val shownValue = if (field == HudField.ALTITUDE) altitude.metres else field.value(flight, model)
            if (alert != null && alert in readingAlerts && shownValue != null)
                warnings[index] = alert.label
        }
        val unitRanges = fields.mapIndexedNotNull { index, field ->
            val suffix = readingUnit(field)
            val unit = if (field == HudField.ENGINE1_MANIFOLD_AUTO && flight.metrics.cockpitAltitudeUnit == null) ""
                else suffix.substringBefore(" · ")
            val value = rows[index].second
            if (unit.isEmpty() || !value.endsWith(" $suffix")) null
            else index to (value.length - suffix.length until value.length - suffix.length + unit.length)
        }.toMap()
        key(t.aircraft, if (compact) altitudeMode else null) {
            FlightReadings(rows, compact, warnings, if (compact) fields.indices.filter { fields[it].id in hiddenLabels }.toSet() else emptySet(), unitRanges)
        }
        if (compact && HudField.SEP in fields) SepStatusPanel(flight, pollingIntervalMs)
        if (compact) FuelEstimateStatusPanel(flight, fields)
        if (compact) MassEstimateStatusPanel(flight, fields, model)
        if (compact && HudField.OIL_PRESSURE_RAW in fields) Text(
            "滑油压力为座舱仪表原值；单位及发动机归属未确定。",
            style = MaterialTheme.typography.bodySmall, color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
        if (compact && HudField.FUEL_PRESSURE_RAW in fields) Text(
            "燃油压力来自未编号座舱仪表，单位及发动机归属未确定。",
            style = MaterialTheme.typography.bodySmall, color = LocalReadingColors.current.label ?: Color(0xFF9EB1C0))
        if (compact && showInstruments && HudField.HEADING in fields) CompassPanel(t.headingDeg, compassHeadingUp)
        if (compact && showInstruments && HudField.AOA in fields) AoaMarginPanel(t, model, aoaBarWarningPercent)
        if (compact && showInstruments && HudField.ENGINE1_THROTTLE in fields) ThrottleBar(flight)
        if (compact && showInstruments && HudField.FUEL_PERCENT in fields) FuelQuantityPanel(flight, readingAlerts)
        if (compact && showInstruments && HudField.FUEL_MASS_SHARE in fields) FuelMassShareBar(flight, model)
        if (compact && showInstruments && HudField.SPEED_LIMIT_RATIO in fields) SpeedLimitBar(flight, model)
        if (mechanization) MechanizationPanel(t, model, showGear, showFlaps, showAirbrake, automaticSweep = compact, alerts = if (compact) readingAlerts else emptyList(), showFlapBar = compact && showFlapBar)
    }
}

private fun Double?.display(digits: Int = 1): String = readingNumber(this, digits)
internal fun statusText(state: ConnectionState): String = when (state) {
    ConnectionState.Delayed -> "遥测更新延迟 · 等待当前请求"
    ConnectionState.Connecting -> "正在连接…"
    ConnectionState.WaitingForFlight -> "已连接 · 等待飞行"
    is ConnectionState.Disconnected -> "连接中断 · 自动重试 · ${state.reason}"
    is ConnectionState.Flying -> "实时 · ${state.telemetry.aircraft ?: "未知机型"}"
}
