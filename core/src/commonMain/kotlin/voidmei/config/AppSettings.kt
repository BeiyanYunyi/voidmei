package voidmei.config

import kotlinx.serialization.json.*
import voidmei.telemetry.HudField
import voidmei.telemetry.HudAltitudeMode

data class WindowPosition(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite()) }
}

data class AppSettings(
    val endpoint: String = "http://127.0.0.1:8111",
    val pollIntervalMs: Long = 100,
    val hudEnabled: Boolean = false,
    val hudOpacity: Float = 0.85f,
    val mainPosition: WindowPosition? = null,
    val hudPosition: WindowPosition? = null,
    val modelWindowPosition: WindowPosition? = null,
    val modelJetWindowPosition: WindowPosition? = null,
    val modelJetWindowEnabled: Boolean = false,
    val modelJetWindowAutoClose: Boolean = false,
    val modelWindowEnabled: Boolean = false,
    val modelWindowHotkeyEnabled: Boolean = false,
    val modelWindowHotkey: String = "Ctrl+Shift+M",
    val modelWindowAlwaysOnTop: Boolean = true,
    val fmDataRoot: String = "data",
    val offlineModels: OfflineModelPreferences = OfflineModelPreferences(),
    val recordingDirectory: String = "records",
    val voiceEnabled: Boolean = false,
    val hudFields: List<String> = HudField.defaults,
    val hudHiddenLabels: List<String> = emptyList(),
    val hudAttitude: Boolean = true,
    val hudAttitudeEarthFixed: Boolean = false,
    val hudCompassHeadingUp: Boolean = false,
    val hudMechanization: Boolean = true,
    val hudGear: Boolean = true,
    val hudFlaps: Boolean = true,
    val hudFlapBar: Boolean = true,
    val hudAirbrake: Boolean = true,
    val hudHotkeyEnabled: Boolean = false,
    val voiceVolume: Int = 100,
    val voiceDirectory: String = "voice",
    val voicePack: String = "default",
    val alertVoices: Map<String, VoiceChoice> = emptyMap(),
    val hudFontScale: Float = 1f,
    val hudEngineIndex: Int? = null,
    val hudEngineFields: List<String> = voidmei.telemetry.HudEngineField.defaults,
    val hudWidthDp: Int = 440,
    val hudReadingColumns: Int = 0,
    val recordingAutoStart: Boolean = false,
    val startInTray: Boolean = false,
    val connectionNotifications: Boolean = false,
    val recordingPerformanceNotifications: Boolean = false,
    val hudCompatibilityMode: Boolean = false,
    val softwareRendering: Boolean = false,
    val hudClickThrough: Boolean = false,
    val hudAutoHideOnFocusLoss: Boolean = false,
    val hudAoaBarWarningPercent: Double = 25.0,
    val hudAoaWarningPercent: Double = 20.0,
    val hudCrosshair: Boolean = false,
    val hudCrosshairSizeDp: Int = 160,
    val hudCrosshairImage: String = "",
    val hudCrosshairStretch: Boolean = false,
    val hudCrosshairRight: Boolean = false,
    val readingColors: Map<String, String> = emptyMap(),
    val hudLabelColor: String? = null,
    val hudValueColor: String? = null,
    val hudWarningColor: String? = null,
    val hudShadeColor: String? = null,
    val hudUnitColor: String? = null,
    val hudAttitudeAoaLimits: Boolean = true,
    val hudAttitudeNorthPointer: Boolean = false,
    val hudAttitudeRefreshMs: Int = 0,
    val hudNumberFont: String? = null,
    val textFont: String? = null,
    val numberFont: String? = null,
    val hudAltitudeMode: HudAltitudeMode = HudAltitudeMode.SEA_LEVEL,
    val hudSceneLayout: HudSceneLayout? = null,
    val hudScenePresets: Map<String, HudSceneLayout> = emptyMap(),
    val hiddenModelSections: Set<ModelDetailSection> = emptySet(),
) {
    init {
        ModelHotkey.parse(modelWindowHotkey)
        require(readingColors.keys.all { it in setOf("label", "value", "warning", "shade", "unit") } && readingColors.values.all { parseHexColor(it) != null })
        require(numberFont == null || (numberFont.isNotBlank() && numberFont.length <= 200 && numberFont.none { it.isISOControl() }))
        require(textFont == null || (textFont.isNotBlank() && textFont.length <= 200 && textFont.none { it.isISOControl() }))
        require(hudNumberFont == null || (hudNumberFont.isNotBlank() && hudNumberFont.length <= 200 && hudNumberFont.none { it.isISOControl() }))
        require(listOf(hudLabelColor, hudValueColor, hudWarningColor, hudShadeColor, hudUnitColor).all { it == null || parseHexColor(it) != null })
        require(hudAttitudeRefreshMs == 0 || hudAttitudeRefreshMs in 10..100)
        require(hudCrosshairSizeDp in 24..400)
        require(hudAoaWarningPercent.isFinite() && hudAoaWarningPercent in 0.0..100.0)
        require(hudAoaBarWarningPercent.isFinite() && hudAoaBarWarningPercent in 0.0..100.0)
        require(voiceDirectory.isNotBlank())
        require(isVoicePackName(voicePack))
        require(voiceVolume in 0..200)
        require(endpoint.isNotBlank())
        require(fmDataRoot.isNotBlank())
        require(recordingDirectory.isNotBlank())
        require(pollIntervalMs in 10..5000)
        require(hudOpacity.isFinite() && hudOpacity in 0f..1f)
        require(hudReadingColumns in 0..16)
        require(hudFontScale.isFinite() && hudFontScale in 0.75f..2f)
        require(hudScenePresets.size <= 16 && hudScenePresets.keys.all {
            it.isNotBlank() && it == it.trim() && it.length <= 80 && it.none { char -> char.isISOControl() }
        })
        require(hudEngineIndex == null || hudEngineIndex > 0)
        require(hudWidthDp in 240..1000)
    }
}

/** Unknown keys survive edits so newer settings are not silently erased by this version. */
object SettingsJson {
    private const val MAX_DEPTH = 64

    /** Scan before recursive JSON parsing; braces inside strings do not introduce containers. */
    private fun checkNesting(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> {
                    depth++
                    require(depth <= MAX_DEPTH) { "配置 JSON 嵌套超过 64 层" }
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "配置 JSON 容器不匹配" }
                }
            }
        }
        // Syntax, matching container types and unterminated strings remain the JSON parser's responsibility.
    }

    /** A transfer must not silently interpret an unrelated versioned JSON document as defaults. */
    fun decodeBackup(text: String, defaultHudCompatibilityMode: Boolean = false): AppSettings {
        val settings = decode(text, defaultHudCompatibilityMode)
        val root = Json.parseToJsonElement(text).jsonObject
        require("endpoint" in root && "pollIntervalMs" in root) { "不是完整的 KMP 设置备份；布局预设请使用预设导入入口" }
        return settings
    }

    fun decode(text: String, defaultHudCompatibilityMode: Boolean = false): AppSettings {
        checkNesting(text)
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported settings version" }
        val defaults = AppSettings()
        fun position(key: String): WindowPosition? = root[key]?.takeUnless { it is JsonNull }?.jsonObject?.let {
            WindowPosition(it.getValue("x").jsonPrimitive.float, it.getValue("y").jsonPrimitive.float)
        }
        return AppSettings(
            hudAutoHideOnFocusLoss = root["hudAutoHideOnFocusLoss"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaults.hudAutoHideOnFocusLoss,
            hudClickThrough = root["hudClickThrough"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaults.hudClickThrough,
            hudCompatibilityMode = root["hudCompatibilityMode"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaultHudCompatibilityMode,
            recordingPerformanceNotifications = root["recordingPerformanceNotifications"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaults.recordingPerformanceNotifications,
            connectionNotifications = root["connectionNotifications"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaults.connectionNotifications,
            startInTray = root["startInTray"]?.jsonPrimitive?.boolean ?: defaults.startInTray,
            recordingAutoStart = root["recordingAutoStart"]?.jsonPrimitive?.let {
                require(!it.isString); it.boolean
            } ?: defaults.recordingAutoStart,
            endpoint = root["endpoint"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: defaults.endpoint,
            pollIntervalMs = root["pollIntervalMs"]?.jsonPrimitive?.long ?: defaults.pollIntervalMs,
            hudAltitudeMode = root["hudAltitudeMode"]?.jsonPrimitive?.let { value ->
                require(value.isString)
                HudAltitudeMode.entries.firstOrNull { it.id == value.content } ?: error("Unknown HUD altitude mode")
            } ?: defaults.hudAltitudeMode,
            readingColors = root["readingColors"]?.jsonObject?.mapValues { (_, value) ->
                value.jsonPrimitive.let { require(it.isString); it.content }
            } ?: defaults.readingColors,
            numberFont = root["numberFont"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            textFont = root["textFont"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudNumberFont = root["hudNumberFont"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudLabelColor = root["hudLabelColor"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudValueColor = root["hudValueColor"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudUnitColor = root["hudUnitColor"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudShadeColor = root["hudShadeColor"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudWarningColor = root["hudWarningColor"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
            hudCrosshairRight = root["hudCrosshairRight"]?.jsonPrimitive?.boolean ?: defaults.hudCrosshairRight,
            hudCrosshairStretch = root["hudCrosshairStretch"]?.jsonPrimitive?.boolean ?: defaults.hudCrosshairStretch,
            hudCrosshairImage = root["hudCrosshairImage"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: defaults.hudCrosshairImage,
            hudCrosshair = root["hudCrosshair"]?.jsonPrimitive?.boolean ?: defaults.hudCrosshair,
            hudCrosshairSizeDp = root["hudCrosshairSizeDp"]?.jsonPrimitive?.int ?: defaults.hudCrosshairSizeDp,
            hudEnabled = root["hudEnabled"]?.jsonPrimitive?.boolean ?: defaults.hudEnabled,
            hudOpacity = root["hudOpacity"]?.jsonPrimitive?.float ?: defaults.hudOpacity,
            hudSceneLayout = root["hudSceneLayout"]?.takeUnless { it == JsonNull }?.let(HudSceneLayout::fromJson),
            hudScenePresets = root["hudScenePresets"]?.jsonObject?.mapValues { HudSceneLayout.fromJson(it.value) } ?: emptyMap(),
            hudFontScale = root["hudFontScale"]?.jsonPrimitive?.let {
                require(!it.isString); it.float
            } ?: defaults.hudFontScale,
            hudEngineIndex = root["hudEngineIndex"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.let {
                require(!it.isString); it.int
            },
            hudWidthDp = root["hudWidthDp"]?.jsonPrimitive?.let {
                require(!it.isString); it.int
            } ?: defaults.hudWidthDp,
            mainPosition = position("mainPosition"), hudPosition = position("hudPosition"),
            modelWindowPosition = position("modelWindowPosition"),
            modelJetWindowPosition = position("modelJetWindowPosition"),
            modelJetWindowAutoClose = root["modelJetWindowAutoClose"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.modelJetWindowAutoClose,
            modelJetWindowEnabled = root["modelJetWindowEnabled"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.modelJetWindowEnabled,
            modelWindowHotkey = root["modelWindowHotkey"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: defaults.modelWindowHotkey,
            modelWindowHotkeyEnabled = root["modelWindowHotkeyEnabled"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.modelWindowHotkeyEnabled,
            modelWindowEnabled = root["modelWindowEnabled"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.modelWindowEnabled,
            modelWindowAlwaysOnTop = root["modelWindowAlwaysOnTop"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.modelWindowAlwaysOnTop,
            hiddenModelSections = root["hiddenModelSections"]?.jsonArray?.map { value ->
                value.jsonPrimitive.let { require(it.isString); ModelDetailSection.valueOf(it.content) }
            }?.toSet() ?: emptySet(),
            offlineModels = root["offlineModels"]?.let(OfflineModelPreferences::fromJson) ?: defaults.offlineModels,
            fmDataRoot = root["fmDataRoot"]?.jsonPrimitive?.content ?: defaults.fmDataRoot,
            recordingDirectory = root["recordingDirectory"]?.jsonPrimitive?.content ?: defaults.recordingDirectory,
            voiceEnabled = root["voiceEnabled"]?.jsonPrimitive?.boolean ?: defaults.voiceEnabled,
            hudHiddenLabels = root["hudHiddenLabels"]?.jsonArray?.map { value ->
                value.jsonPrimitive.let { require(it.isString); it.content }
            }?.distinct() ?: defaults.hudHiddenLabels,
            hudEngineFields = root["hudEngineFields"]?.jsonArray?.map { value ->
                value.jsonPrimitive.let { require(it.isString); it.content }
            }?.distinct() ?: defaults.hudEngineFields,
            hudFields = root["hudFields"]?.jsonArray?.map { value ->
                value.jsonPrimitive.let { require(it.isString); it.content }
            }?.distinct() ?: defaults.hudFields,
            hudCompassHeadingUp = root["hudCompassHeadingUp"]?.jsonPrimitive?.boolean ?: defaults.hudCompassHeadingUp,
            hudAttitudeRefreshMs = root["hudAttitudeRefreshMs"]?.jsonPrimitive?.let { require(!it.isString); it.int } ?: defaults.hudAttitudeRefreshMs,
            hudAttitudeNorthPointer = root["hudAttitudeNorthPointer"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.hudAttitudeNorthPointer,
            hudAttitudeAoaLimits = root["hudAttitudeAoaLimits"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.hudAttitudeAoaLimits,
            hudAttitudeEarthFixed = root["hudAttitudeEarthFixed"]?.jsonPrimitive?.boolean ?: defaults.hudAttitudeEarthFixed,
            hudAttitude = root["hudAttitude"]?.jsonPrimitive?.boolean ?: defaults.hudAttitude,
            hudGear = root["hudGear"]?.jsonPrimitive?.boolean ?: defaults.hudGear,
            hudFlapBar = root["hudFlapBar"]?.jsonPrimitive?.boolean ?: defaults.hudFlapBar,
            hudFlaps = root["hudFlaps"]?.jsonPrimitive?.boolean ?: defaults.hudFlaps,
            hudAirbrake = root["hudAirbrake"]?.jsonPrimitive?.boolean ?: defaults.hudAirbrake,
            hudAoaWarningPercent = root["hudAoaWarningPercent"]?.jsonPrimitive?.double ?: defaults.hudAoaWarningPercent,
            hudAoaBarWarningPercent = root["hudAoaBarWarningPercent"]?.jsonPrimitive?.double ?: defaults.hudAoaBarWarningPercent,
            hudMechanization = root["hudMechanization"]?.jsonPrimitive?.boolean ?: defaults.hudMechanization,
            hudHotkeyEnabled = root["hudHotkeyEnabled"]?.jsonPrimitive?.boolean ?: defaults.hudHotkeyEnabled,
            hudReadingColumns = root["hudReadingColumns"]?.jsonPrimitive?.let { require(!it.isString); it.int } ?: defaults.hudReadingColumns,
            softwareRendering = root["softwareRendering"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: defaults.softwareRendering,
            voiceVolume = root["voiceVolume"]?.jsonPrimitive?.int ?: defaults.voiceVolume,
            voiceDirectory = root["voiceDirectory"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: defaults.voiceDirectory,
            voicePack = root["voicePack"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: defaults.voicePack,
            alertVoices = root["alertVoices"]?.jsonObject?.mapValues { (_, value) ->
                val choice = value.jsonObject
                VoiceChoice(
                    enabled = choice["enabled"]?.jsonPrimitive?.boolean ?: true,
                    pack = choice["pack"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content },
                )
            } ?: emptyMap(),
        )
    }

    fun encode(settings: AppSettings, previous: String? = null, prettyPrint: Boolean = true): String {
        val fields = previous?.let {
            decode(it) // Refuse malformed or future-version documents before overwriting anything.
            Json.parseToJsonElement(it).jsonObject.toMutableMap()
        } ?: mutableMapOf()
        fields["version"] = JsonPrimitive(1)
        fields["endpoint"] = JsonPrimitive(settings.endpoint)
        fields["pollIntervalMs"] = JsonPrimitive(settings.pollIntervalMs)
        fields["hudLabelColor"] = settings.hudLabelColor?.let { JsonPrimitive(it) } ?: JsonNull
        fields["hudValueColor"] = settings.hudValueColor?.let { JsonPrimitive(it) } ?: JsonNull
        fields["hudUnitColor"] = settings.hudUnitColor?.let { JsonPrimitive(it) } ?: JsonNull
        fields["hudShadeColor"] = settings.hudShadeColor?.let { JsonPrimitive(it) } ?: JsonNull
        fields["hudWarningColor"] = settings.hudWarningColor?.let { JsonPrimitive(it) } ?: JsonNull
        fields["hudCrosshairRight"] = JsonPrimitive(settings.hudCrosshairRight)
        fields["hudCrosshairStretch"] = JsonPrimitive(settings.hudCrosshairStretch)
        fields["hudCrosshairImage"] = JsonPrimitive(settings.hudCrosshairImage)
        fields["hudCrosshair"] = JsonPrimitive(settings.hudCrosshair)
        fields["hudCrosshairSizeDp"] = JsonPrimitive(settings.hudCrosshairSizeDp)
        fields["hudEnabled"] = JsonPrimitive(settings.hudEnabled)
        fields["hudOpacity"] = JsonPrimitive(settings.hudOpacity)
        fields["hudSceneLayout"] = settings.hudSceneLayout?.toJson() ?: JsonNull
        fields["hudScenePresets"] = JsonObject(settings.hudScenePresets.mapValues { it.value.toJson() })
        fields["hudFontScale"] = JsonPrimitive(settings.hudFontScale)
        fields["hudWidthDp"] = JsonPrimitive(settings.hudWidthDp)
        fields["hudEngineIndex"] = settings.hudEngineIndex?.let(::JsonPrimitive) ?: JsonNull
        fields["offlineModels"] = settings.offlineModels.toJson()
        fields["hiddenModelSections"] = JsonArray(settings.hiddenModelSections.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) })
        fields["fmDataRoot"] = JsonPrimitive(settings.fmDataRoot)
        fields["recordingDirectory"] = JsonPrimitive(settings.recordingDirectory)
        fields["recordingPerformanceNotifications"] = JsonPrimitive(settings.recordingPerformanceNotifications)
        fields["connectionNotifications"] = JsonPrimitive(settings.connectionNotifications)
        fields["readingColors"] = JsonObject(settings.readingColors.mapValues { JsonPrimitive(it.value) })
        fields["startInTray"] = JsonPrimitive(settings.startInTray)
        fields["recordingAutoStart"] = JsonPrimitive(settings.recordingAutoStart)
        fields["hudReadingColumns"] = JsonPrimitive(settings.hudReadingColumns)
        fields["softwareRendering"] = JsonPrimitive(settings.softwareRendering)
        fields["hudCompatibilityMode"] = JsonPrimitive(settings.hudCompatibilityMode)
        fields["hudClickThrough"] = JsonPrimitive(settings.hudClickThrough)
        fields["hudAutoHideOnFocusLoss"] = JsonPrimitive(settings.hudAutoHideOnFocusLoss)
        fields["voiceEnabled"] = JsonPrimitive(settings.voiceEnabled)
        fields["voiceVolume"] = JsonPrimitive(settings.voiceVolume)
        fields["voiceDirectory"] = JsonPrimitive(settings.voiceDirectory)
        fields["voicePack"] = JsonPrimitive(settings.voicePack)
        fields["alertVoices"] = JsonObject(settings.alertVoices.mapValues { (_, choice) ->
            buildJsonObject { put("enabled", choice.enabled); put("pack", choice.pack?.let(::JsonPrimitive) ?: JsonNull) }
        })
        fields["hudHiddenLabels"] = JsonArray(settings.hudHiddenLabels.map(::JsonPrimitive))
        fields["hudEngineFields"] = JsonArray(settings.hudEngineFields.map(::JsonPrimitive))
        fields["hudFields"] = JsonArray(settings.hudFields.map(::JsonPrimitive))
        fields["hudCompassHeadingUp"] = JsonPrimitive(settings.hudCompassHeadingUp)
        fields["hudAltitudeMode"] = JsonPrimitive(settings.hudAltitudeMode.id)
        fields["numberFont"] = settings.numberFont?.let(::JsonPrimitive) ?: JsonNull
        fields["textFont"] = settings.textFont?.let(::JsonPrimitive) ?: JsonNull
        fields["hudNumberFont"] = settings.hudNumberFont?.let(::JsonPrimitive) ?: JsonNull
        fields["hudAttitudeRefreshMs"] = JsonPrimitive(settings.hudAttitudeRefreshMs)
        fields["hudAttitudeNorthPointer"] = JsonPrimitive(settings.hudAttitudeNorthPointer)
        fields["hudAttitudeAoaLimits"] = JsonPrimitive(settings.hudAttitudeAoaLimits)
        fields["hudAttitudeEarthFixed"] = JsonPrimitive(settings.hudAttitudeEarthFixed)
        fields["hudAttitude"] = JsonPrimitive(settings.hudAttitude)
        fields["hudGear"] = JsonPrimitive(settings.hudGear)
        fields["hudFlapBar"] = JsonPrimitive(settings.hudFlapBar)
        fields["hudFlaps"] = JsonPrimitive(settings.hudFlaps)
        fields["hudAirbrake"] = JsonPrimitive(settings.hudAirbrake)
        fields["hudAoaWarningPercent"] = JsonPrimitive(settings.hudAoaWarningPercent)
        fields["hudAoaBarWarningPercent"] = JsonPrimitive(settings.hudAoaBarWarningPercent)
        fields["hudMechanization"] = JsonPrimitive(settings.hudMechanization)
        fields["hudHotkeyEnabled"] = JsonPrimitive(settings.hudHotkeyEnabled)
        fun position(key: String, value: WindowPosition?) {
            fields[key] = value?.let {
                buildJsonObject { put("x", it.x); put("y", it.y) }
            } ?: JsonNull
        }
        position("mainPosition", settings.mainPosition)
        position("hudPosition", settings.hudPosition)
        position("modelWindowPosition", settings.modelWindowPosition)
        position("modelJetWindowPosition", settings.modelJetWindowPosition)
        fields["modelJetWindowAutoClose"] = JsonPrimitive(settings.modelJetWindowAutoClose)
        fields["modelJetWindowEnabled"] = JsonPrimitive(settings.modelJetWindowEnabled)
        fields["modelWindowHotkey"] = JsonPrimitive(settings.modelWindowHotkey)
        fields["modelWindowHotkeyEnabled"] = JsonPrimitive(settings.modelWindowHotkeyEnabled)
        fields["modelWindowEnabled"] = JsonPrimitive(settings.modelWindowEnabled)
        fields["modelWindowAlwaysOnTop"] = JsonPrimitive(settings.modelWindowAlwaysOnTop)
        return Json { this.prettyPrint = prettyPrint }.encodeToString(JsonObject.serializer(), JsonObject(fields))
    }
}
