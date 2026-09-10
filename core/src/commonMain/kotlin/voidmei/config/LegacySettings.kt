package voidmei.config

import voidmei.telemetry.HudAltitudeMode

data class LegacyCrosshairImage(val name: String, val enabled: Boolean?, val sizeDp: Int?)

data class UnmigratedLegacySetting(val label: String, val target: String)

/** Only settings whose meaning survives the layout rewrite are imported. */
data class LegacySettings(val intervalMs: Long?, val hudEnabled: Boolean?, val voiceEnabled: Boolean?, val voiceVolume: Int? = null, val alertVoices: Map<String, VoiceChoice> = emptyMap(),
    val hudFieldChoices: Map<String, Boolean> = emptyMap(), val hudAttitude: Boolean? = null,
    val hudAutoHideOnFocusLoss: Boolean? = null,
    val unmigrated: List<UnmigratedLegacySetting> = emptyList(),
    val recordingAutoStart: Boolean? = null,
    val hudGear: Boolean? = null, val hudFlaps: Boolean? = null, val hudAirbrake: Boolean? = null,
    val hudAoaBarWarningPercent: Double? = null, val hudAoaWarningPercent: Double? = null, val hudFlapBar: Boolean? = null,
    val hudCompassHeadingUp: Boolean? = null, val startInTray: Boolean? = null, val hiddenLabelChoices: Map<String, Boolean> = emptyMap(), val hudCrosshair: Boolean? = null, val hudCrosshairSizeDp: Int? = null, val hudCrosshairImage: String? = null, val pendingCrosshairImage: LegacyCrosshairImage? = null, val hudCrosshairStretch: Boolean? = null, val hudReadingColors: Map<String, String> = emptyMap(), val hudAttitudeAoaLimits: Boolean? = null, val hudNumberFont: String? = null, val hudAltitudeMode: HudAltitudeMode? = null, val httpPort: Int? = null) {
    val movesCrosshairRight: Boolean get() = hudCrosshair == true || hudCrosshairSizeDp != null || hudCrosshairImage != null
    val hasChanges: Boolean get() = httpPort != null || hudAltitudeMode != null || hudNumberFont != null || hudAttitudeAoaLimits != null || hudReadingColors.isNotEmpty() || intervalMs != null || hudEnabled != null || voiceEnabled != null ||
        voiceVolume != null || alertVoices.isNotEmpty() || hudFieldChoices.isNotEmpty() ||
        hudAttitude != null || hudAutoHideOnFocusLoss != null || recordingAutoStart != null ||
        hudGear != null || hudFlaps != null || hudAirbrake != null || hudAoaBarWarningPercent != null || hudAoaWarningPercent != null || hudFlapBar != null || hudCompassHeadingUp != null || startInTray != null || hiddenLabelChoices.isNotEmpty() || hudCrosshair != null || hudCrosshairSizeDp != null || hudCrosshairImage != null || hudCrosshairStretch != null
    fun applyTo(current: AppSettings) = current.copy(
        endpoint = httpPort?.let { replaceTelemetryPort(current.endpoint, it) } ?: current.endpoint,
        hudAltitudeMode = hudAltitudeMode ?: current.hudAltitudeMode,
        hudNumberFont = hudNumberFont ?: current.hudNumberFont,
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
        voiceEnabled = voiceEnabled ?: current.voiceEnabled,
        voiceVolume = voiceVolume ?: current.voiceVolume,
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
        hudFields = (current.hudFields.filter { hudFieldChoices[it] != false } +
            hudFieldChoices.filterValues { it }.keys.filter { it !in current.hudFields }).distinct(),
    )
}

object LegacySettingsReader {
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
        val unmigrated = mutableListOf<UnmigratedLegacySetting>()
        val targets = mutableMapOf<String, Pair<String, String>>()
        val voiceKeys = voidmei.telemetry.FlightAlert.entries.map { "voice_${it.voice}" }.toSet()
        val colorKeys = setOf("fontLabel", "fontNum", "fontUnit", "fontWarn", "fontShade")
        val supported = colorKeys + voiceKeys + fieldIds.keys + hudSwitchFields.keys + setOf("httpPort", "alwaysShowRadarAltitude", "MonoNumFont", "attitudeIndicatorDisplayAoALimits", "displayCrosshair", "crosshairName", "crosshairScale", "disableHUDSpeedLabel", "disableHUDHeightLabel", "disableHUDSEPLabel", "autoStartGameMode", "attitudeIndicatorInertialMode", "enableFlapAngleBar", "showSpeedBar", "miniHUDaoaWarningRatio", "miniHUDaoaBarWarningRatio", "showHUDGear", "showHUDFlaps", "showHUDAirbrake", "drawHUDtext", "showHUDSpeed", "hudMach", "enableLogging", "autoHideOnFocusLoss", "showAttitudeGauge", "dataPollIntervalMs", "Interval", "crosshairSwitch", "enableVoiceWarn", "voiceVolume")
        fun walk(node: Node) {
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
                if (target in supported) {
                    require(target !in targets) { "旧配置重复设置 $target" }
                    targets[target!!] = (field(":type") ?: error("$target 缺少类型")) to
                        (field(":value") ?: error("$target 缺少值"))
                } else if (target != null) {
                    unmigrated += UnmigratedLegacySetting(children.getOrNull(1)?.atom ?: target, target)
                }
            }
            if (kind != "item") children.forEach(::walk)
        }
        roots.forEach(::walk)
        val numberFont = targets["MonoNumFont"]?.let { (type, value) ->
            require(type == "combo") { "MonoNumFont 类型不支持" }
            value.trim().takeIf { it.isNotEmpty() && it.length <= 200 && it.none { char -> char.isISOControl() } }
                ?: run { unmigrated += UnmigratedLegacySetting("字体名称无效，保留当前字体", "MonoNumFont"); null }
        }
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
                unmigrated += UnmigratedLegacySetting("HUD 表格以外的全局配色", target)
                target to hex
            }
        }.toMap()
        val interval = (targets["dataPollIntervalMs"] ?: targets["Interval"])?.let { (type, value) ->
            require(type in setOf("slider", "input")) { "刷新间隔类型不支持" }
            value.toLongOrNull()?.also { require(it in 20..5000) { "刷新间隔需在 20–5000 ms 内" } }
                ?: error("刷新间隔不是整数")
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
        val httpPort = targets["httpPort"]?.let { (type, value) ->
            require(type == "input") { "httpPort 类型不支持" }
            requireNotNull(value.toIntOrNull()?.takeIf { it in 1..65535 }) { "httpPort 需在 1–65535 内" }
        }
        return LegacySettings(interval, flag("crosshairSwitch"), flag("enableVoiceWarn"), volume, choices,
            fields, mechanicalVisibility("showAttitudeGauge"), flag("autoHideOnFocusLoss"), unmigrated, flag("enableLogging"),
            mechanicalVisibility("showHUDGear"), mechanicalVisibility("showHUDFlaps"),
            mechanicalVisibility("showHUDAirbrake"), aoaPercent("miniHUDaoaBarWarningRatio"), aoaPercent("miniHUDaoaWarningRatio"), mechanicalVisibility("enableFlapAngleBar"), flag("attitudeIndicatorInertialMode"), flag("autoStartGameMode"), buildMap {
                flag("disableHUDSpeedLabel")?.let { put("ias", it); put("mach", it) }
                flag("disableHUDHeightLabel")?.let { put("altitude", it) }
                flag("disableHUDSEPLabel")?.let { put("sep", it) }
            }, showCrosshair, crosshairSize, pendingCrosshairImage = if (!vectorCrosshair)
                LegacyCrosshairImage(crosshairName!!, flag("displayCrosshair"),
                    targets["crosshairScale"]?.second?.toIntOrNull()?.times(2)?.takeIf { it in 24..400 }) else null, httpPort = httpPort, hudAltitudeMode = flag("alwaysShowRadarAltitude")?.let { if (it) HudAltitudeMode.ALWAYS_RADAR else HudAltitudeMode.LOW_RADAR }, hudNumberFont = numberFont, hudReadingColors = readingColors, hudAttitudeAoaLimits = flag("attitudeIndicatorDisplayAoALimits")).also {
            require(it.hasChanges || it.unmigrated.isNotEmpty()) { "未找到可迁移的设置" }
        }
    }
}
