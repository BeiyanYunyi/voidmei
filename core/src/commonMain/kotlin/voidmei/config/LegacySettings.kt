package voidmei.config

import voidmei.telemetry.HudAltitudeMode

data class LegacyCrosshairImage(val name: String, val enabled: Boolean?, val sizeDp: Int?)

data class UnmigratedLegacySetting(val label: String, val target: String, val sourcePath: List<String> = emptyList())

/** Only settings whose meaning survives the layout rewrite are imported. */
data class LegacySettings(val intervalMs: Long?, val hudEnabled: Boolean?, val voiceEnabled: Boolean?, val voiceVolume: Int? = null, val alertVoices: Map<String, VoiceChoice> = emptyMap(),
    val hudFieldChoices: Map<String, Boolean> = emptyMap(), val hudAttitude: Boolean? = null,
    val hudAutoHideOnFocusLoss: Boolean? = null,
    val unmigrated: List<UnmigratedLegacySetting> = emptyList(),
    val recordingAutoStart: Boolean? = null,
    val hudGear: Boolean? = null, val hudFlaps: Boolean? = null, val hudAirbrake: Boolean? = null,
    val hudAoaBarWarningPercent: Double? = null, val hudAoaWarningPercent: Double? = null, val hudFlapBar: Boolean? = null,
    val hudCompassHeadingUp: Boolean? = null, val startInTray: Boolean? = null, val hiddenLabelChoices: Map<String, Boolean> = emptyMap(), val hudCrosshair: Boolean? = null, val hudCrosshairSizeDp: Int? = null, val hudCrosshairImage: String? = null, val pendingCrosshairImage: LegacyCrosshairImage? = null, val hudCrosshairStretch: Boolean? = null, val hudReadingColors: Map<String, String> = emptyMap(), val hudAttitudeAoaLimits: Boolean? = null, val hudNumberFont: String? = null, val hudAltitudeMode: HudAltitudeMode? = null, val httpPort: Int? = null, val textFont: String? = null, val numberFont: String? = null, val connectionNotifications: Boolean? = null, val recordingPerformanceNotifications: Boolean? = null, val softwareRendering: Boolean? = null, val softwareRenderingSource: String? = null, val hudEngineFieldChoices: Map<String, Boolean> = emptyMap(), val voiceDirectory: String? = null,
    val hudPositions: Map<HudRegionContent, LegacyHudPosition> = emptyMap(), val importHudPositions: Boolean = false,
    val createMissingHudRegions: Boolean = false,
    val hudRegionVisibility: Map<HudRegionContent, Boolean> = emptyMap(),
    val importHudRegionVisibility: Boolean = false,
    val legacyScreenSize: LegacyScreenSize? = null,
    val hudAttitudeNorthPointer: Boolean? = null,
    val hudAttitudeRefreshMs: Int? = null,
    val modelHotkey: String? = null, val importModelHotkey: Boolean = false,
    val engineControlStyle: LegacyEngineControlStyle? = null, val engineControlStyleRegionId: String? = null,
    val powerTextSizes: ReadingTextSizes? = null, val powerTextSizesRegionId: String? = null,
    val enginePanelFonts: Map<String, String> = emptyMap(),
    val engineFontTargets: Map<String, String> = emptyMap(),
    val engineRegionsToCreate: List<HudRegion> = emptyList(),
    val enginePanelPositions: Map<String, LegacyHudPosition> = emptyMap(),
    val enginePositionTargets: Map<String, String> = emptyMap(),
    val enginePanelVisibility: Map<String, Boolean> = emptyMap(),
    val enginePanelTargets: Map<String, String> = emptyMap(),
    val modelSectionChoices: Map<ModelDetailSection, Boolean> = emptyMap(), val importModelSections: Boolean = false,
    val engineAircraftFuel: Boolean? = null, val engineAircraftFuelRegionId: String? = null,
    val engineReadingColumns: Int? = null, val engineColumnsRegionId: String? = null,
    val flightReadingColumns: Int? = null, val importFlightReadingColumns: Boolean = false,
    val flightLabelFont: String? = null, val importFlightLabelFont: Boolean = false,
    val flightTextSizes: ReadingTextSizes? = null, val importFlightTextSizes: Boolean = false,
    val hudRegionBorders: Map<HudRegionContent, Boolean> = emptyMap(), val importHudRegionBorders: Boolean = false,
    val attitudeSize: LegacyAttitudeSize? = null, val importAttitudeSize: Boolean = false, val legacyDpiScale: Double? = null) {
    val movesCrosshairRight: Boolean get() = hudCrosshair == true || hudCrosshairSizeDp != null || hudCrosshairImage != null
    val hasChanges: Boolean get() = (importModelHotkey && modelHotkey != null) || hudAttitudeRefreshMs != null || (importModelSections && modelSectionChoices.isNotEmpty()) || (engineAircraftFuel != null && engineAircraftFuelRegionId != null) || (engineControlStyle != null && engineControlStyleRegionId != null) || (powerTextSizes != null && powerTextSizesRegionId != null) || engineFontTargets.keys.any { it in enginePanelFonts } || engineRegionsToCreate.isNotEmpty() || enginePositionTargets.keys.any { it in enginePanelPositions } || enginePanelTargets.keys.any { it in enginePanelVisibility } || (engineReadingColumns != null && engineColumnsRegionId != null) || (importAttitudeSize && attitudeSize != null) || (importHudRegionBorders && hudRegionBorders.isNotEmpty()) || (importFlightTextSizes && flightTextSizes != null) || (importFlightLabelFont && flightLabelFont != null) || (importFlightReadingColumns && flightReadingColumns != null) || hudAttitudeNorthPointer != null || (importHudRegionVisibility && hudRegionVisibility.isNotEmpty()) || (importHudPositions && hudPositions.isNotEmpty()) || voiceDirectory != null || hudEngineFieldChoices.isNotEmpty() || softwareRendering != null || recordingPerformanceNotifications != null || numberFont != null || textFont != null || httpPort != null || hudAltitudeMode != null || hudNumberFont != null || hudAttitudeAoaLimits != null || hudReadingColors.isNotEmpty() || intervalMs != null || hudEnabled != null || voiceEnabled != null ||
        voiceVolume != null || alertVoices.isNotEmpty() || hudFieldChoices.isNotEmpty() ||
        hudAttitude != null || hudAutoHideOnFocusLoss != null || recordingAutoStart != null || connectionNotifications != null ||
        hudGear != null || hudFlaps != null || hudAirbrake != null || hudAoaBarWarningPercent != null || hudAoaWarningPercent != null || hudFlapBar != null || hudCompassHeadingUp != null || startInTray != null || hiddenLabelChoices.isNotEmpty() || hudCrosshair != null || hudCrosshairSizeDp != null || hudCrosshairImage != null || hudCrosshairStretch != null
    fun applyToScene(current: HudSceneLayout?): HudSceneLayout? {
        val baseCreated = if (importHudPositions && createMissingHudRegions) current?.let { scene ->
            (hudPositions.keys - scene.regions.map { it.content }.toSet()).fold(scene) { layout, type -> layout.addRegion(type) }
        } else current
        require(engineRegionsToCreate.all { it.content == HudRegionContent.ENGINE }) { "仅支持补建发动机分区" }
        require(engineRegionsToCreate.map { it.id }.distinct().size == engineRegionsToCreate.size) { "补建分区 ID 重复" }
        val created = baseCreated?.let { scene ->
            val additions = engineRegionsToCreate.filter { candidate -> scene.regions.none { it.id == candidate.id } }
            require(scene.regions.size + additions.size <= 32) { "补建后超过 32 个分区" }
            scene.copy(regions = scene.regions + additions)
        }
        val sized = if (importAttitudeSize && attitudeSize != null && created != null) attitudeSize.applyTo(created,
            requireNotNull(legacyScreenSize) { "尺寸迁移需要原屏幕宽高" }, requireNotNull(legacyDpiScale) { "尺寸迁移需要原 DPI 缩放" }) else created
        val basePositioned = if (importHudPositions) sized?.withLegacyPositions(hudPositions, false, legacyScreenSize) else sized
        val selectedEnginePositions = enginePositionTargets.filterKeys { it in enginePanelPositions }
        require(selectedEnginePositions.values.distinct().size == selectedEnginePositions.size) { "动力信息和引擎控制位置必须选择不同分区" }
        val positioned = basePositioned?.copy(regions = basePositioned.regions.map { region ->
            val key = selectedEnginePositions.entries.firstOrNull { it.value == region.id }?.key
            if (key != null && region.content == HudRegionContent.ENGINE)
                region.withLegacyPosition(enginePanelPositions.getValue(key), basePositioned.width, basePositioned.height, legacyScreenSize)
            else region
        })
        val regionVisibility = if (importHudRegionVisibility) positioned?.withLegacyVisibility(hudRegionVisibility) else positioned
        val selectedEnginePanels = enginePanelTargets.filterKeys { it in enginePanelVisibility }
        require(selectedEnginePanels.values.distinct().size == selectedEnginePanels.size) { "动力信息和引擎控制必须选择不同分区" }
        val visibility = regionVisibility?.copy(regions = regionVisibility.regions.map { region ->
            val key = selectedEnginePanels.entries.firstOrNull { it.value == region.id }?.key
            if (key != null && region.content == HudRegionContent.ENGINE)
                region.copy(visible = enginePanelVisibility.getValue(key)) else region
        })
        val bordered = if (importHudRegionBorders && visibility != null) {
            val firstIds = hudRegionBorders.keys.mapNotNull { type -> visibility.regions.firstOrNull { it.content == type }?.id }.toSet()
            visibility.copy(regions = visibility.regions.map { if (it.id in firstIds) it.copy(borderEnabled = hudRegionBorders.getValue(it.content)) else it })
        } else visibility
        val fueled = if (engineAircraftFuel != null && engineAircraftFuelRegionId != null) bordered?.copy(
            regions = bordered.regions.map { if (it.id == engineAircraftFuelRegionId && it.content == HudRegionContent.ENGINE)
                it.copy(showAircraftFuel = engineAircraftFuel) else it }) else bordered
        val visible = if (engineReadingColumns != null && engineColumnsRegionId != null) fueled?.copy(
            regions = fueled.regions.map { if (it.id == engineColumnsRegionId && it.content == HudRegionContent.ENGINE)
                it.copy(readingColumns = engineReadingColumns) else it }) else fueled
        val selectedEngineFonts = engineFontTargets.filterKeys { it in enginePanelFonts }
        require(selectedEngineFonts.values.distinct().size == selectedEngineFonts.size) { "动力信息和引擎控制字体必须选择不同分区" }
        val styled = visible?.copy(regions = visible.regions.map { region ->
            val key = selectedEngineFonts.entries.firstOrNull { it.value == region.id }?.key
            if (key != null && region.content == HudRegionContent.ENGINE)
                region.copy(readingLabelFont = enginePanelFonts.getValue(key)) else region
        })
        val textSized = if (powerTextSizes != null && powerTextSizesRegionId != null) styled?.copy(regions = styled.regions.map {
            if (it.id == powerTextSizesRegionId && it.content == HudRegionContent.ENGINE)
                it.copy(readingTextSizes = powerTextSizes, readingTextWeights = ReadingTextWeights.legacyFlight, fontScale = 1f) else it
        }) else styled
        require(engineControlStyle == null || powerTextSizes == null || engineControlStyleRegionId == null ||
            engineControlStyleRegionId != powerTextSizesRegionId) { "动力字号和引擎控制字号必须选择不同分区" }
        val controlSized = if (engineControlStyle != null && engineControlStyleRegionId != null) textSized?.copy(regions = textSized.regions.map {
            if (it.id == engineControlStyleRegionId && it.content == HudRegionContent.ENGINE) engineControlStyle.applyTo(it) else it
        }) else textSized
        val firstFlight = controlSized?.regions?.firstOrNull { it.content == HudRegionContent.FLIGHT }
        return if (firstFlight != null) controlSized.copy(regions = controlSized.regions.map {
            if (it.id != firstFlight.id) it else it.copy(
                readingTextWeights = if (importFlightTextSizes && flightTextSizes != null) ReadingTextWeights.legacyFlight else it.readingTextWeights,
                readingTextSizes = if (importFlightTextSizes) flightTextSizes ?: it.readingTextSizes else it.readingTextSizes,
                fontScale = if (importFlightTextSizes && flightTextSizes != null) 1f else it.fontScale,
                readingColumns = if (importFlightReadingColumns) flightReadingColumns ?: it.readingColumns else it.readingColumns,
                readingLabelFont = if (importFlightLabelFont) flightLabelFont ?: it.readingLabelFont else it.readingLabelFont)
        }) else controlSized
    }

    fun applyTo(current: AppSettings) = current.copy(
        hiddenModelSections = if (importModelSections) (current.hiddenModelSections - modelSectionChoices.filterValues { it }.keys) +
            modelSectionChoices.filterValues { !it }.keys else current.hiddenModelSections,
        hudSceneLayout = applyToScene(current.hudSceneLayout),
        softwareRendering = softwareRendering ?: current.softwareRendering,
        readingColors = current.readingColors + hudReadingColors.mapKeys { (key, _) ->
            mapOf("fontLabel" to "label", "fontNum" to "value", "fontWarn" to "warning", "fontShade" to "shade", "fontUnit" to "unit").getValue(key)
        },
        textFont = textFont ?: current.textFont,
        numberFont = numberFont ?: current.numberFont,
        endpoint = httpPort?.let { replaceTelemetryPort(current.endpoint, it) } ?: current.endpoint,
        hudAltitudeMode = hudAltitudeMode ?: current.hudAltitudeMode,
        hudNumberFont = hudNumberFont ?: current.hudNumberFont,
        modelWindowHotkey = if (importModelHotkey && !modelHotkey.isNullOrEmpty()) modelHotkey else current.modelWindowHotkey,
        modelWindowHotkeyEnabled = if (importModelHotkey && modelHotkey == "") false else current.modelWindowHotkeyEnabled,
        hudAttitudeRefreshMs = hudAttitudeRefreshMs ?: current.hudAttitudeRefreshMs,
        hudAttitudeNorthPointer = hudAttitudeNorthPointer ?: current.hudAttitudeNorthPointer,
        hudAttitudeAoaLimits = hudAttitudeAoaLimits ?: current.hudAttitudeAoaLimits,
        hudLabelColor = hudReadingColors["fontLabel"] ?: current.hudLabelColor,
        hudValueColor = hudReadingColors["fontNum"] ?: current.hudValueColor,
        hudUnitColor = hudReadingColors["fontUnit"] ?: current.hudUnitColor,
        hudWarningColor = hudReadingColors["fontWarn"] ?: current.hudWarningColor,
        hudShadeColor = hudReadingColors["fontShade"] ?: current.hudShadeColor,
        pollIntervalMs = intervalMs ?: current.pollIntervalMs,
        hudEnabled = hudEnabled ?: current.hudEnabled,
        hudCrosshair = hudCrosshair ?: current.hudCrosshair,
        hudCrosshairImage = hudCrosshairImage ?: if (hudCrosshair == true || hudCrosshairSizeDp != null) "" else current.hudCrosshairImage,
        hudCrosshairSizeDp = hudCrosshairSizeDp ?: current.hudCrosshairSizeDp,
        hudCrosshairStretch = hudCrosshairStretch ?: current.hudCrosshairStretch,
        hudCrosshairRight = if (movesCrosshairRight) true else current.hudCrosshairRight,
        hudHiddenLabels = current.hudHiddenLabels.filter { hiddenLabelChoices[it] != false } +
            hiddenLabelChoices.filter { it.value && it.key !in current.hudHiddenLabels }.keys,
        voiceDirectory = voiceDirectory ?: current.voiceDirectory,
        voiceEnabled = voiceEnabled ?: current.voiceEnabled,
        voiceVolume = voiceVolume ?: current.voiceVolume,
        recordingPerformanceNotifications = recordingPerformanceNotifications ?: current.recordingPerformanceNotifications,
        connectionNotifications = connectionNotifications ?: current.connectionNotifications,
        recordingAutoStart = recordingAutoStart ?: current.recordingAutoStart,
        alertVoices = current.alertVoices + alertVoices,
        hudCompassHeadingUp = hudCompassHeadingUp ?: current.hudCompassHeadingUp,
        startInTray = startInTray ?: current.startInTray,
        hudAttitudeEarthFixed = hudCompassHeadingUp ?: current.hudAttitudeEarthFixed,
        hudAttitude = hudAttitude ?: current.hudAttitude,
        hudAoaWarningPercent = hudAoaWarningPercent ?: current.hudAoaWarningPercent,
        hudAoaBarWarningPercent = hudAoaBarWarningPercent ?: current.hudAoaBarWarningPercent,
        hudGear = hudGear ?: current.hudGear,
        hudFlapBar = hudFlapBar ?: current.hudFlapBar,
        hudFlaps = hudFlaps ?: current.hudFlaps,
        hudAirbrake = hudAirbrake ?: current.hudAirbrake,
        hudAutoHideOnFocusLoss = hudAutoHideOnFocusLoss ?: current.hudAutoHideOnFocusLoss,
        hudEngineFields = (current.hudEngineFields.filter { hudEngineFieldChoices[it] != false } +
            hudEngineFieldChoices.filterValues { it }.keys.filter { it !in current.hudEngineFields }).distinct(),
        hudFields = (current.hudFields.filter { hudFieldChoices[it] != false } +
            hudFieldChoices.filterValues { it }.keys.filter { it !in current.hudFields }).distinct(),
    )
}

object LegacySettingsReader {
    private val engineControlFields = linkedMapOf(
        "disableEngineInfoThrottle" to "throttle", "disableEngineInfoPitch" to "rpm_control",
        "disableEngineInfoMixture" to "mixture", "disableEngineInfoRadiator" to "radiator",
        "disableEngineInfoCompressor" to "compressor")
    private val aircraftControlFields = linkedMapOf("disableEngineInfoPower" to "power_percent",
        "disableEngineInfoLFuel" to "fuel_percent")
    private val hudSwitchFields = linkedMapOf("showHUDAltitude" to "altitude", "showHUDEnergy" to "energy",
        "showHUDSep" to "sep", "showHUDGLoad" to "load", "showHUDAoA" to "aoa", "showHUDManeuverBar" to "fuel_mass_share")
    private val fieldIds = linkedMapOf(
        "getIAS" to "ias", "getTAS" to "tas", "getMach" to "mach", "getAltitude" to "altitude", "getCompass" to "heading",
        "getVario" to "climb", "getSEP" to "sep", "getAcceleration" to "acceleration",
        "getRollRate" to "roll_rate", "getNy" to "load", "getTurnRate" to "turn_rate",
        "getTurnRadius" to "turn_radius", "getAoA" to "aoa", "getAoS" to "sideslip",
        "getRadioAltitude" to "radio_altitude_estimate", "getWingSweep * 100" to "wing_sweep",
        "getWepKg" to "wep_fuel", "getWepTime" to "wep_time",
        "getBoosterFuelKg" to "booster_fuel", "getBoosterFuelPercent" to "booster_fuel_percent",
        "getEngineResponse" to "engine_response",
        "getHeatTolerance" to "heat_tolerance",
        "getPowerPercent" to "power_percent",
        "getPropEfficiency" to "propulsive_efficiency",
        "getHorsePower" to "power", "getEffHp" to "thrust_power", "getMassFuel" to "fuel",
        "getFuelTimeMili * 0.001" to "endurance_clock",
        "getTotalWeight" to "mass_estimate",
        "getWaterTemp" to "engine_temperature", "getOilTemp" to "oil_temperature",
        "getManifoldPressureDisplay" to "engine1_manifold_auto",
        "getThrust" to "engine1_thrust", "getRPM" to "engine1_rpm", "getPitch" to "engine1_pitch")
    const val MAX_BYTES = 1024 * 1024
    private data class Node(val atom: String? = null, val quoted: Boolean = false, val children: List<Node>? = null)

    fun read(text: String): LegacySettings {
        require(text.length <= MAX_BYTES) { "旧配置超过 1 MiB" }
        var offset = 0
        var nodes = 0
        fun skip() {
            while (offset < text.length) {
                if (text[offset].isWhitespace() || (offset == 0 && text[offset] == '\uFEFF')) offset++
                else if (text[offset] == ';') { while (offset < text.length && text[offset] != '\n') offset++ }
                else break
            }
        }
        fun parse(depth: Int): Node {
            require(depth <= 64 && ++nodes <= 100_000) { "旧配置结构过大" }
            skip()
            require(offset < text.length) { "旧配置未结束" }
            return when (text[offset++]) {
                '(' -> {
                    val children = mutableListOf<Node>()
                    while (true) {
                        skip()
                        require(offset < text.length) { "旧配置缺少右括号" }
                        if (text[offset] == ')') { offset++; break }
                        children += parse(depth + 1)
                    }
                    Node(children = children)
                }
                ')' -> error("旧配置多余右括号")
                '"' -> {
                    val value = StringBuilder()
                    while (true) {
                        require(offset < text.length) { "旧配置字符串未结束" }
                        var c = text[offset++]
                        if (c == '"') break
                        if (c == '\\') {
                            require(offset < text.length) { "旧配置转义未结束" }
                            c = text[offset++] // Matches the legacy parser's literal escape behavior.
                        }
                        value.append(c)
                    }
                    Node(value.toString(), quoted = true)
                }
                else -> {
                    val start = offset - 1
                    while (offset < text.length && !text[offset].isWhitespace() && text[offset] !in "();") offset++
                    Node(text.substring(start, offset))
                }
            }
        }
        val roots = mutableListOf<Node>()
        skip()
        while (offset < text.length) { roots += parse(0); skip() }
        require(roots.isNotEmpty() && roots.all { it.children?.firstOrNull()?.let { n -> !n.quoted && n.atom == "panel" } == true }) {
            "仅支持旧版 ui_layout.user.cfg 的 panel 格式"
        }
        var flightPanelFont: String? = null
        var flightTextSizes: ReadingTextSizes? = null
        var powerSizes: ReadingTextSizes? = null
        var controlStyle: LegacyEngineControlStyle? = null
        val engineFonts = linkedMapOf<String, String>()
        val enginePositions = linkedMapOf<String, LegacyHudPosition>()
        val positions = linkedMapOf<HudRegionContent, LegacyHudPosition>()
        val panelContents = mapOf("飞行信息" to HudRegionContent.FLIGHT, "地平仪" to HudRegionContent.ATTITUDE,
            "舵面值" to HudRegionContent.CONTROLS, "起落襟翼" to HudRegionContent.MECHANIZATION)
        roots.forEach { root ->
            val children = root.children!!
            val engineKey = mapOf("动力信息" to "engineInfoSwitch", "引擎控制" to "enableEngineControl")[children.getOrNull(1)?.atom]
            val content = panelContents[children.getOrNull(1)?.atom]
            if (content == null && engineKey == null) return@forEach
            if (content == HudRegionContent.FLIGHT || engineKey != null) {
                val sizeIndices = children.indices.filter { !children[it].quoted && children[it].atom == ":font-size" }
                require(sizeIndices.size <= 1) { "旧配置重复属性 :font-size" }
                sizeIndices.singleOrNull()?.let { index ->
                    val offset = requireNotNull(children.getOrNull(index + 1)?.atom?.toIntOrNull()?.takeIf { it in -6..(if (engineKey == "engineInfoSwitch") 18 else 20) }) {
                        "旧字号偏移无效：飞行／引擎控制允许 -6–20，动力允许 -6–18 的整数"
                    }
                    if (engineKey == "engineInfoSwitch") {
                        require(powerSizes == null) { "旧配置重复动力字号属性" }
                        powerSizes = ReadingTextSizes.fromLegacyOffset(offset)
                    } else if (engineKey == "enableEngineControl") {
                        require(controlStyle == null) { "旧配置重复引擎控制字号属性" }
                        controlStyle = LegacyEngineControlStyle(offset)
                    } else if (flightTextSizes == null) flightTextSizes = ReadingTextSizes.fromLegacyOffset(offset)
                }
            }
            if (content == HudRegionContent.FLIGHT || engineKey != null) {
                val indices = children.indices.filter { !children[it].quoted && children[it].atom == ":font" }
                require(indices.size <= 1) { "旧配置重复属性 :font" }
                indices.singleOrNull()?.let { index ->
                    val value = children.getOrNull(index + 1)
                    val font = requireNotNull(value?.atom?.takeUnless { !value.quoted && it.startsWith(":") }) { "旧配置属性缺值 :font" }.trim()
                    require(font.length <= 200 && font.none { it.isISOControl() }) { "旧面板字体名称无效" }
                    if (font.isNotEmpty()) {
                        if (engineKey != null) {
                            require(engineKey !in engineFonts) { "旧配置重复窗口字体 ${children[1].atom}" }
                            engineFonts[engineKey] = font
                        } else if (flightPanelFont == null) flightPanelFont = font
                    }
                }
            }
            fun coordinate(key: String): Double? {
                val indices = children.indices.filter { !children[it].quoted && children[it].atom == key }
                require(indices.size <= 1) { "旧配置重复属性 $key" }
                return indices.singleOrNull()?.let { index ->
                    requireNotNull(children.getOrNull(index + 1)?.atom?.toDoubleOrNull()?.takeIf { it.isFinite() }) {
                        "旧窗口坐标 $key 无效"
                    }
                }
            }
            val x = coordinate(":x")
            val y = coordinate(":y")
            if (x != null || y != null) {
                if (engineKey != null) {
                    require(engineKey !in enginePositions) { "旧配置重复窗口 ${children[1].atom}" }
                    enginePositions[engineKey] = LegacyHudPosition(x ?: .1, y ?: .1)
                } else {
                    require(content !in positions) { "旧配置重复窗口 ${children[1].atom}" }
                    positions[requireNotNull(content)] = LegacyHudPosition(x ?: .1, y ?: .1)
                }
            }
        }
        val unmigrated = mutableListOf<UnmigratedLegacySetting>()
        val targets = mutableMapOf<String, Pair<String, String>>()
        val voiceKeys = voidmei.telemetry.FlightAlert.entries.map { "voice_${it.voice}" }.toSet()
        val colorKeys = setOf("fontLabel", "fontNum", "fontUnit", "fontWarn", "fontShade")
        val regionSwitches = mapOf("flightInfoSwitch" to HudRegionContent.FLIGHT,
            "enableAxis" to HudRegionContent.CONTROLS, "enableAttitudeIndicator" to HudRegionContent.ATTITUDE,
            "enablegearAndFlaps" to HudRegionContent.MECHANIZATION)
        val borderKeys = mapOf("flightInfoEdge" to HudRegionContent.FLIGHT, "enableAxisEdge" to HudRegionContent.CONTROLS,
            "enableAttitudeIndicatorEdge" to HudRegionContent.ATTITUDE, "enablegearAndFlapsEdge" to HudRegionContent.MECHANIZATION)
        val enginePanelKeys = setOf("engineInfoSwitch", "enableEngineControl")
        val supported = listOf("displayFmKey") + LegacyModelCategory.entries.map { it.target } + enginePanelKeys + borderKeys.keys + regionSwitches.keys + engineControlFields.keys + aircraftControlFields.keys + colorKeys + voiceKeys + fieldIds.keys + hudSwitchFields.keys + setOf("attitudeIndicatorFreqMs", "attitudeIndicatorWidth", "attitudeIndicatorHeight", "flightInfoFontC", "flightInfoColumn", "attitudeIndicatorDisplayDirection", "gpuCompatibilityMode", "enableAltInformation", "enableStatusBar", "GlobalTextFont", "GlobalNumFont", "httpPort", "alwaysShowRadarAltitude", "MonoNumFont", "attitudeIndicatorDisplayAoALimits", "displayCrosshair", "crosshairName", "crosshairScale", "disableHUDSpeedLabel", "disableHUDHeightLabel", "disableHUDSEPLabel", "autoStartGameMode", "attitudeIndicatorInertialMode", "enableFlapAngleBar", "showSpeedBar", "miniHUDaoaWarningRatio", "miniHUDaoaBarWarningRatio", "showHUDGear", "showHUDFlaps", "showHUDAirbrake", "drawHUDtext", "showHUDSpeed", "hudMach", "enableLogging", "autoHideOnFocusLoss", "showAttitudeGauge", "dataPollIntervalMs", "Interval", "crosshairSwitch", "enableVoiceWarn", "voiceVolume")
        val targetSources = mutableMapOf<String, List<String>>()
        var flightRowSize: Int? = null
        var engineColumns: Int? = null
        var engineRowFont: String? = null
        var powerRowSize: Int? = null
        var controlRowSize: Int? = null
        fun walk(node: Node, panelTitle: String?, path: List<String>) {
            val children = node.children ?: return
            val kind = children.firstOrNull()?.takeUnless { it.quoted }?.atom
            if (kind !in setOf("panel", "group", "item")) return
            if (kind == "item") {
                fun field(key: String): String? {
                    val indices = children.indices.filter { !children[it].quoted && children[it].atom == key }
                    require(indices.size <= 1) { "旧配置重复属性 $key" }
                    return indices.singleOrNull()?.let { i ->
                        require(i + 1 < children.size && children[i + 1].atom != null) { "旧配置属性缺值 $key" }
                        children[i + 1].atom
                    }
                }
                val target = field(":target")
                if (target == "fontSize" && panelTitle == "飞行信息") {
                    require(flightRowSize == null) { "旧飞行面板重复字号设置" }
                    require(field(":type") == "slider") { "旧飞行字号类型不支持" }
                    flightRowSize = requireNotNull(field(":value")?.toIntOrNull()?.takeIf { it in -6..20 }) {
                        "旧飞行字号偏移需为 -6–20 内的整数"
                    }
                } else if (target == "fontSize" && panelTitle == "动力信息") {
                    require(powerRowSize == null) { "旧动力面板重复字号设置" }
                    require(field(":type") == "slider") { "旧动力字号类型不支持" }
                    powerRowSize = requireNotNull(field(":value")?.toIntOrNull()?.takeIf { it in -6..18 }) { "旧动力字号需为 -6–18 内的整数" }
                } else if (target == "fontSize" && panelTitle == "引擎控制") {
                    require(controlRowSize == null) { "旧引擎控制面板重复字号设置" }
                    require(field(":type") == "slider") { "旧引擎控制字号类型不支持" }
                    controlRowSize = requireNotNull(field(":value")?.toIntOrNull()?.takeIf { it in -6..20 }) { "旧引擎控制字号需为 -6–20 内的整数" }
                } else if (target == "hudColumns" && panelTitle == "动力信息") {
                    require(engineColumns == null) { "旧动力面板重复列数设置" }
                    require(field(":type") == "slider") { "旧动力列数类型不支持" }
                    engineColumns = requireNotNull(field(":value")?.toIntOrNull()?.takeIf { it in 1..8 }) {
                        "旧动力列数需为 1–8 内的整数"
                    }
                } else if (target == "fontName" && panelTitle == "动力信息") {
                    require(engineRowFont == null) { "旧动力面板重复字体设置" }
                    require(field(":type") == "combo") { "旧动力字体类型不支持" }
                    engineRowFont = requireNotNull(field(":value")) { "旧动力字体缺值" }.trim()
                    require(engineRowFont!!.isNotEmpty() && engineRowFont!!.length <= 200 && engineRowFont!!.none { it.isISOControl() }) { "旧动力字体名称无效" }
                } else if (target in supported) {
                    require(target !in targets) { "旧配置重复设置 $target" }
                    targetSources[target!!] = path
                    targets[target] = (field(":type") ?: error("$target 缺少类型")) to
                        (field(":value") ?: error("$target 缺少值"))
                } else if (target != null) {
                    unmigrated += UnmigratedLegacySetting(children.getOrNull(1)?.atom ?: target, target, path)
                }
            }
            if (kind != "item") {
                val nextPath = if (kind == "group") path + listOfNotNull(children.getOrNull(1)?.atom) else path
                children.forEach { walk(it, panelTitle, nextPath) }
            }
        }
        // Java searches panels in file order: an explicit row in that panel wins over its switch-key.
        val resolvedRegionSwitches = linkedMapOf<String, Pair<String, String>>()
        roots.forEach { root ->
            val previousTargets = targets.keys.toSet()
            walk(root, root.children?.getOrNull(1)?.atom, listOfNotNull(root.children?.getOrNull(1)?.atom))
            val children = root.children!!
            fun panelAttribute(key: String): String? {
                val indices = children.indices.filter { !children[it].quoted && children[it].atom == key }
                require(indices.size <= 1) { "旧配置重复属性 $key" }
                return indices.singleOrNull()?.let { index ->
                    val value = children.getOrNull(index + 1)
                    requireNotNull(value?.atom?.takeUnless { !value.quoted && it.startsWith(":") }) { "旧配置属性缺值 $key" }
                }
            }
            val panelTitle = children.getOrNull(1)?.atom
            if (panelTitle in panelContents && panelAttribute(":alpha") != null)
                unmigrated += UnmigratedLegacySetting("${panelTitle}的旧透明度未转换；请在分区设置中分别调整背景和内容", ":alpha", listOfNotNull(panelTitle))
            val switchKey = panelAttribute(":switch-key")
            (regionSwitches.keys + enginePanelKeys).forEach { key ->
                if (key !in resolvedRegionSwitches) {
                    if (key in targets && key !in previousTargets) resolvedRegionSwitches[key] = targets.getValue(key)
                    else if (switchKey == key) {
                        val visible = panelAttribute(":visible") ?: "false"
                        require(visible.toBooleanStrictOrNull() != null) { "旧面板显示状态不是布尔值" }
                        resolvedRegionSwitches[key] = "switch" to visible
                    }
                }
            }
            if (switchKey != null && switchKey !in regionSwitches && switchKey !in enginePanelKeys)
                unmigrated += UnmigratedLegacySetting("${children.getOrNull(1)?.atom ?: "旧面板"}的面板总开关", switchKey, listOfNotNull(panelTitle))
        }
        targets.putAll(resolvedRegionSwitches)
        val modelHotkey = targets["displayFmKey"]?.let { (type, value) ->
            require(type == "hotkey") { "displayFmKey 类型不支持" }
            val code = requireNotNull(value.toIntOrNull()?.takeIf { it in 0..65535 }) { "displayFmKey 编码无效" }
            if (code == 0) "" else ModelHotkeyKey.entries.singleOrNull { it.nativeCode == code }?.name
        }
        if ("displayFmKey" in targets && modelHotkey == null)
            unmigrated += UnmigratedLegacySetting("不支持的模型热键编码：${targets.getValue("displayFmKey").second}", "displayFmKey", targetSources["displayFmKey"].orEmpty())

        val textFont = targets["GlobalTextFont"]?.let { (type, value) ->
            require(type == "combo") { "GlobalTextFont 类型不支持" }
            value.trim().takeIf { it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() } }
                ?: run { unmigrated += UnmigratedLegacySetting("文字字体名称无效，保留当前字体", "GlobalTextFont"); null }
        }
        val monoFont = targets["MonoNumFont"]
        val globalFont = targets["GlobalNumFont"]
        for ((key, entry) in listOf("MonoNumFont" to monoFont, "GlobalNumFont" to globalFont)) {
            if (entry != null) require(entry.first == "combo") { "$key 类型不支持" }
        }
        val globalNumberFont = globalFont?.second?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() }
        }
        if (globalFont != null && globalNumberFont == null)
            unmigrated += UnmigratedLegacySetting("数字字体名称无效，保留当前字体", "GlobalNumFont")
        val fontSource = if (monoFont == null || monoFont.second.isBlank()) "GlobalNumFont" else "MonoNumFont"
        val numberFont = if (fontSource == "GlobalNumFont") globalNumberFont else targets[fontSource]?.second?.let { value ->
            value.trim().takeIf { it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() } }
                ?: run { unmigrated += UnmigratedLegacySetting("字体名称无效，保留当前字体", fontSource); null }
        }
        if (monoFont != null && monoFont.second.isBlank() && globalFont == null)
            unmigrated += UnmigratedLegacySetting("未指定等宽或全局数字字体，保留当前字体", "MonoNumFont")
        val readingColors = targets.filterKeys { it in colorKeys }.mapNotNull { (target, entry) ->
            require(entry.first == "color") { "$target 类型不支持" }
            val text = entry.second.trim()
            val hex = if (parseHexColor(text) != null) text.uppercase() else {
                val parts = text.split(',').map { it.trim().toIntOrNull() }
                if (parts.size in 3..4 && parts.all { it != null }) {
                    val rgba = parts.map { it!!.coerceIn(0, 255) } + if (parts.size == 3) listOf(255) else emptyList()
                    "#" + rgba.joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
                } else null
            }
            if (hex == null) {
                unmigrated += UnmigratedLegacySetting("颜色无效，保留当前配色", target)
                null
            } else {
                unmigrated += UnmigratedLegacySetting("表格以外的全局配色", target)
                target to hex
            }
        }.toMap()
        val intervalTarget = if ("dataPollIntervalMs" in targets) "dataPollIntervalMs" else "Interval"
        val interval = targets[intervalTarget]?.let { (type, value) ->
            require(type in setOf("slider", "input")) { "刷新间隔类型不支持" }
            val parsed = value.toLongOrNull()?.also { require(it in 10..5000) { "旧刷新间隔需在 10–5000 ms 内" } }
                ?: error("刷新间隔不是整数")
            parsed
        }
        fun flag(key: String) = targets[key]?.let { (type, value) ->
            require(type == "switch" || type == "switch-inv") { "$key 类型不支持" }
            val bool = value.toBooleanStrictOrNull() ?: error("$key 不是布尔值")
            if (type == "switch-inv") !bool else bool
        }
        val volume = targets["voiceVolume"]?.let { (type, value) ->
            require(type == "slider" || type == "input") { "语音音量类型不支持" }
            value.toIntOrNull()?.also { require(it in 0..200) { "语音音量需在 0–200 内" } }
                ?: error("语音音量不是整数")
        }
        val choices = targets.filterKeys { it in voiceKeys }.mapKeys { it.key.removePrefix("voice_") }.mapValues { (key, entry) ->
            require(entry.first == "voice") { "$key 语音配置类型不支持" }
            val parts = entry.second.split('|')
            require(parts.size <= 2) { "$key 语音配置无效" }
            val pack = parts[0].ifEmpty { "default" }
            val enabled = if (parts.size == 1) true else parts[1].toBooleanStrictOrNull()
                ?: error("$key 语音开关无效")
            VoiceChoice(enabled, pack)
        }
        val fields = targets.filterKeys { it in fieldIds }.map { (target, entry) ->
            require(entry.first == "data") { "$target 数据开关类型不支持" }
            fieldIds.getValue(target) to (entry.second.toBooleanStrictOrNull() ?: error("$target 不是布尔值"))
        }.toMap().toMutableMap()
        val engineChoices = engineControlFields.mapNotNull { (target, id) -> flag(target)?.let { id to !it } }.toMap()
        aircraftControlFields.forEach { (target, id) ->
            flag(target)?.let { disabled -> fields[id] = fields[id] == true || !disabled }
        }
        val textVisible = flag("drawHUDtext")
        hudSwitchFields.forEach { (target, id) ->
            val enabled = flag(target)
            if (enabled != null || textVisible != null) {
                fields[id] = (fields[id] == true) || ((textVisible ?: true) && (enabled ?: true))
            }
        }
        val attitude = flag("showAttitudeGauge")
        if (attitude != null || textVisible != null) {
            fields["heading"] = fields["heading"] == true || ((textVisible ?: true) && !(attitude ?: true))
        }
        val speedBar = flag("showSpeedBar")
        if (speedBar != null || textVisible != null) {
            fields["speed_limit_ratio"] = (textVisible ?: true) && (speedBar ?: true)
            fields["engine1_throttle"] = (textVisible ?: true) && !(speedBar ?: true)
        }
        val showSpeed = flag("showHUDSpeed")
        val machMode = flag("hudMach")
        if (showSpeed != null || machMode != null || textVisible != null) {
            val visible = (textVisible ?: true) && (showSpeed ?: true)
            val mach = machMode ?: false
            fields["ias"] = fields["ias"] == true || (visible && !mach)
            fields["mach"] = fields["mach"] == true || (visible && mach)
        }
        fun mechanicalVisibility(target: String): Boolean? {
            val child = flag(target)
            return if (child == null && textVisible == null) null else (textVisible ?: true) && (child ?: true)
        }
        fun aoaPercent(target: String) = targets[target]?.let { (type, value) ->
            require(type in setOf("slider", "input")) { "$target 阈值类型不支持" }
            val number = value.toDoubleOrNull()
            require(number != null && number.isFinite() && number in 0.0..100.0) { "$target 阈值无效" }
            if (number <= 1.0) number * 100 else number
        }
        val crosshairName = targets["crosshairName"]?.let { (type, value) ->
            require(type == "combo") { "crosshairName 类型不支持" }
            value.trim()
        }
        val vectorCrosshair = crosshairName.isNullOrEmpty() || crosshairName == "软件渲染准星"
        if (!vectorCrosshair) unmigrated += UnmigratedLegacySetting("图片准星尚未迁移", "crosshairName")
        val showCrosshair = flag("displayCrosshair")?.let {
            if (it && !vectorCrosshair) {
                unmigrated += UnmigratedLegacySetting("保留当前准星开关，未替换图片样式", "displayCrosshair")
                null
            } else it
        }
        val crosshairSize = targets["crosshairScale"]?.let { (type, value) ->
            require(type in setOf("slider", "input")) { "crosshairScale 类型不支持" }
            val scale = value.toIntOrNull()
            require(scale != null && scale in 0..200) { "crosshairScale 需在 0–200 内" }
            if (vectorCrosshair && scale * 2 in 24..400) scale * 2 else {
                unmigrated += UnmigratedLegacySetting("准星尺寸或图片样式无法换算，保留当前尺寸", "crosshairScale")
                null
            }
        }
        if (crosshairName != null && vectorCrosshair && showCrosshair == null && crosshairSize == null)
            unmigrated += UnmigratedLegacySetting("线框样式已支持，文件未提供可应用的开关或尺寸", "crosshairName")
        val attitudeRefresh = targets["attitudeIndicatorFreqMs"]?.let { (type, value) ->
            require(type == "slider") { "attitudeIndicatorFreqMs 类型不支持" }
            requireNotNull(value.toIntOrNull()?.takeIf { it in 10..100 }) { "姿态刷新间隔需为 10–100 ms" }
        }
        val httpPort = targets["httpPort"]?.let { (type, value) ->
            require(type == "input") { "httpPort 类型不支持" }
            requireNotNull(value.toIntOrNull()?.takeIf { it in 1..65535 }) { "httpPort 需在 1–65535 内" }
        }
        val flightLabelFont = targets["flightInfoFontC"]?.let { (type, value) ->
            require(type == "combo") { "flightInfoFontC 类型不支持" }
            value.trim().takeIf { it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() } }
                ?: run { unmigrated += UnmigratedLegacySetting("飞行标签字体名称无效，保留当前字体", "flightInfoFontC"); null }
        }
        val flightColumns = targets["flightInfoColumn"]?.let { (type, value) ->
            require(type == "slider") { "flightInfoColumn 类型不支持" }
            requireNotNull(value.toIntOrNull()?.takeIf { it in 1..16 }) { "旧飞行列数需在 1–16 内" }
        }
        fun attitudeDimension(key: String): Int? = targets[key]?.let { (type, value) ->
            require(type == "slider") { "$key 类型不支持" }
            requireNotNull(value.toIntOrNull()?.takeIf { it in 100..600 }) { "旧姿态宽高需为 100–600 内的整数" }
        }
        val attitudeWidth = attitudeDimension("attitudeIndicatorWidth")
        val attitudeHeight = attitudeDimension("attitudeIndicatorHeight")
        val attitudeSize = if (attitudeWidth != null || attitudeHeight != null)
            LegacyAttitudeSize(attitudeWidth ?: 150, attitudeHeight ?: 300, flag("enableAttitudeIndicatorEdge") ?: false) else null
        return LegacySettings(interval, flag("crosshairSwitch"), flag("enableVoiceWarn"), volume, choices,
            fields, mechanicalVisibility("showAttitudeGauge"), flag("autoHideOnFocusLoss"), unmigrated, flag("enableLogging"),
            mechanicalVisibility("showHUDGear"), mechanicalVisibility("showHUDFlaps"),
            mechanicalVisibility("showHUDAirbrake"), aoaPercent("miniHUDaoaBarWarningRatio"), aoaPercent("miniHUDaoaWarningRatio"), mechanicalVisibility("enableFlapAngleBar"), flag("attitudeIndicatorInertialMode"), flag("autoStartGameMode"), buildMap {
                flag("disableHUDSpeedLabel")?.let { put("ias", it); put("mach", it) }
                flag("disableHUDHeightLabel")?.let { put("altitude", it) }
                flag("disableHUDSEPLabel")?.let { put("sep", it) }
            }, showCrosshair, crosshairSize, engineControlStyle = controlStyle ?: controlRowSize?.let(::LegacyEngineControlStyle), powerTextSizes = powerSizes ?: powerRowSize?.let(ReadingTextSizes::fromLegacyOffset), enginePanelFonts = (engineRowFont?.let { mapOf("engineInfoSwitch" to it) }.orEmpty() + engineFonts), enginePanelPositions = enginePositions, enginePanelVisibility = enginePanelKeys.mapNotNull { key -> flag(key)?.let { key to it } }.toMap(), modelSectionChoices = LegacyModelCategory.entries.mapNotNull { category -> flag(category.target)?.let { category.section to it } }.toMap(), engineAircraftFuel = flag("disableEngineInfoLFuel")?.not(), engineReadingColumns = engineColumns, flightReadingColumns = flightColumns, flightLabelFont = flightPanelFont ?: flightLabelFont, flightTextSizes = flightTextSizes ?: flightRowSize?.let(ReadingTextSizes::fromLegacyOffset), attitudeSize = attitudeSize, hudRegionBorders = borderKeys.mapNotNull { (key, content) -> flag(key)?.let { content to it } }.toMap(), hudPositions = positions, hudRegionVisibility = regionSwitches.mapNotNull { (key, content) -> flag(key)?.let { content to it } }.toMap(), hudEngineFieldChoices = engineChoices, softwareRendering = flag("gpuCompatibilityMode"), recordingPerformanceNotifications = flag("enableAltInformation"), connectionNotifications = flag("enableStatusBar"), pendingCrosshairImage = if (!vectorCrosshair)
                LegacyCrosshairImage(crosshairName!!, flag("displayCrosshair"),
                    targets["crosshairScale"]?.second?.toIntOrNull()?.times(2)?.takeIf { it in 24..400 }) else null, textFont = textFont, numberFont = globalNumberFont, httpPort = httpPort, hudAltitudeMode = flag("alwaysShowRadarAltitude")?.let { if (it) HudAltitudeMode.ALWAYS_RADAR else HudAltitudeMode.LOW_RADAR }, hudNumberFont = numberFont, hudReadingColors = readingColors, modelHotkey = modelHotkey, hudAttitudeRefreshMs = attitudeRefresh, hudAttitudeNorthPointer = flag("attitudeIndicatorDisplayDirection"), hudAttitudeAoaLimits = flag("attitudeIndicatorDisplayAoALimits")).also {
            require(it.hasChanges || it.modelHotkey != null || it.modelSectionChoices.isNotEmpty() || it.engineControlStyle != null || it.powerTextSizes != null || it.enginePanelFonts.isNotEmpty() || it.enginePanelPositions.isNotEmpty() || it.enginePanelVisibility.isNotEmpty() || it.engineReadingColumns != null || it.attitudeSize != null || it.hudRegionBorders.isNotEmpty() || it.flightTextSizes != null || it.flightLabelFont != null || it.flightReadingColumns != null || it.hudPositions.isNotEmpty() || it.hudRegionVisibility.isNotEmpty() || it.unmigrated.isNotEmpty()) { "未找到可迁移的设置" }
        }.let { settings ->
            settings.copy(unmigrated = settings.unmigrated.map { item ->
                if (item.sourcePath.isNotEmpty()) item else item.copy(sourcePath = targetSources[item.target].orEmpty())
            })
        }
    }
}
