package voidmei.config

import kotlinx.serialization.json.*

enum class HudRegionContent { FLIGHT, ENGINE, ATTITUDE, MECHANIZATION, ALERTS }

/** Coordinates are relative to one transparent window; list order defines stacking. */
data class HudRegion(
    val id: String,
    val content: HudRegionContent,
    val x: Int, val y: Int, val width: Int, val height: Int,
    val backgroundAlpha: Float = 0.5f,
    val contentAlpha: Float = 1f,
    val engineIndex: Int = 1,
) {
    init {
        require(id.isNotBlank() && id.length <= 100 && id.none { it.isISOControl() })
        require(x in 0..8192 && y in 0..8192 && width in 80..8192 && height in 40..8192)
        require(backgroundAlpha.isFinite() && backgroundAlpha in 0f..1f)
        require(contentAlpha.isFinite() && contentAlpha in 0f..1f)
        require(engineIndex > 0)
    }
}

data class HudSceneLayout(val width: Int, val height: Int, val regions: List<HudRegion>, val enabled: Boolean = true) {
    init {
        require(width in 240..8192 && height in 120..8192)
        require(regions.size in 1..32 && regions.map { it.id }.distinct().size == regions.size)
        require(regions.all { it.x + it.width <= width && it.y + it.height <= height })
    }

    fun toJson() = buildJsonObject {
        put("width", width); put("height", height)
        put("enabled", enabled)
        put("regions", JsonArray(regions.map { region -> buildJsonObject {
            put("id", region.id); put("content", region.content.name)
            put("x", region.x); put("y", region.y); put("width", region.width); put("height", region.height)
            put("backgroundAlpha", region.backgroundAlpha); put("contentAlpha", region.contentAlpha)
            put("engineIndex", region.engineIndex)
        } }))
    }

    companion object {
        fun fromJson(value: JsonElement): HudSceneLayout {
            val root = value.jsonObject
            fun JsonObject.integer(key: String) = getValue(key).jsonPrimitive.let { require(!it.isString); it.int }
            fun JsonObject.alpha(key: String) = getValue(key).jsonPrimitive.let { require(!it.isString); it.float }
            fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.let { require(it.isString); it.content }
            return HudSceneLayout(root.integer("width"), root.integer("height"), root.getValue("regions").jsonArray.map {
                val r = it.jsonObject
                HudRegion(r.text("id"), HudRegionContent.valueOf(r.text("content")), r.integer("x"), r.integer("y"),
                    r.integer("width"), r.integer("height"), r.alpha("backgroundAlpha"), r.alpha("contentAlpha"), r.integer("engineIndex"))
            }, root["enabled"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: true)
        }

        fun initial(settings: AppSettings) = HudSceneLayout(1280, 720, buildList {
            add(HudRegion("flight", HudRegionContent.FLIGHT, 16, 16, 440, 470, settings.hudOpacity))
            add(HudRegion("alerts", HudRegionContent.ALERTS, 470, 16, 340, 180, settings.hudOpacity))
            if (settings.hudEngineIndex != null) add(HudRegion("engine", HudRegionContent.ENGINE, 824, 16, 440, 470,
                settings.hudOpacity, engineIndex = settings.hudEngineIndex))
            if (settings.hudAttitude) add(HudRegion("attitude", HudRegionContent.ATTITUDE, 470, 500, 340, 204, settings.hudOpacity))
            if (settings.hudMechanization) add(HudRegion("mechanization", HudRegionContent.MECHANIZATION, 16, 500, 440, 204, settings.hudOpacity))
        })
    }
}
