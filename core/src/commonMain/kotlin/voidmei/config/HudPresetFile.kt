package voidmei.config

import kotlinx.serialization.json.*

/** Standalone layout backup; global settings and external resources are not embedded. */
object HudPresetFile {
    const val MAX_BYTES = 1024 * 1024

    fun encode(presets: Map<String, HudSceneLayout>): String {
        AppSettings(hudScenePresets = presets) // Same name/count constraints as persisted settings.
        return buildJsonObject {
            put("format", "voidmei-hud-presets")
            put("version", 1)
            put("presets", JsonObject(presets.mapValues { it.value.toJson() }))
        }.toString()
    }

    fun decode(text: String): Map<String, HudSceneLayout> {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["format"] == JsonPrimitive("voidmei-hud-presets")) { "不是 VoidMei HUD 预设文件" }
        require(root["version"] == JsonPrimitive(1)) { "不支持的 HUD 预设版本" }
        val presets = root.getValue("presets").jsonObject.mapValues { HudSceneLayout.fromJson(it.value) }
        return AppSettings(hudScenePresets = presets).hudScenePresets
    }
}
