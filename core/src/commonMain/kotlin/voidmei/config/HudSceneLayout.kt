package voidmei.config

import kotlinx.serialization.json.*

enum class HudRegionContent(val label: String) {
    FLIGHT("飞行读数"), ENGINE("发动机"), ATTITUDE("姿态"), MECHANIZATION("机械化"), ALERTS("告警")
}

/** Coordinates are relative to one transparent window; list order defines stacking. */
data class HudRegion(
    val id: String,
    val content: HudRegionContent,
    val x: Int, val y: Int, val width: Int, val height: Int,
    val backgroundAlpha: Float = 0.5f,
    val contentAlpha: Float = 1f,
    val engineIndex: Int = 1,
    /** Null inherits global fields; empty explicitly hides all readings in this region. */
    val fields: List<String>? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 100 && id.none { it.isISOControl() })
        require(x in 0..8192 && y in 0..8192 && width in 80..8192 && height in 40..8192)
        require(backgroundAlpha.isFinite() && backgroundAlpha in 0f..1f)
        require(contentAlpha.isFinite() && contentAlpha in 0f..1f)
        require(engineIndex > 0)
        require(fields == null || fields.all { it.isNotBlank() })
    }
}

data class HudSceneLayout(val width: Int, val height: Int, val regions: List<HudRegion>, val enabled: Boolean = true) {
    init {
        require(width in 240..8192 && height in 120..8192)
        require(regions.size in 1..32 && regions.map { it.id }.distinct().size == regions.size)
        require(regions.all { it.x + it.width <= width && it.y + it.height <= height })
    }

    fun addRegion(content: HudRegionContent): HudSceneLayout {
        require(regions.size < 32)
        val id = (1..33).map { "region-$it" }.first { candidate -> regions.none { it.id == candidate } }
        val engine = (1..33).first { index -> regions.none { it.content == HudRegionContent.ENGINE && it.engineIndex == index } }
        val regionWidth = minOf(width, if (content == HudRegionContent.ATTITUDE) 340 else 440)
        val regionHeight = minOf(height, when (content) {
            HudRegionContent.FLIGHT, HudRegionContent.ENGINE -> 470
            HudRegionContent.ATTITUDE, HudRegionContent.MECHANIZATION -> 204
            HudRegionContent.ALERTS -> 180
        })
        // Stagger new regions so overlapping instances do not look like one unchanged region.
        val offset = 16 * (regions.size + 1)
        return copy(regions = regions + HudRegion(id, content, minOf(offset, width - regionWidth),
            minOf(offset, height - regionHeight), regionWidth, regionHeight, engineIndex = engine))
    }

    fun removeRegion(id: String): HudSceneLayout {
        val remaining = regions.filterNot { it.id == id }
        require(remaining.isNotEmpty()) { "Keep at least one HUD region" }
        return copy(regions = remaining)
    }

    /** Later regions paint over earlier ones; reordering never changes region geometry or fields. */
    fun moveRegionLayer(id: String, towardFront: Boolean): HudSceneLayout {
        val index = regions.indexOfFirst { it.id == id }
        if (index < 0) return this
        val target = index + if (towardFront) 1 else -1
        if (target !in regions.indices) return this
        return copy(regions = regions.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun moveRegion(id: String, x: Int, y: Int): HudSceneLayout = copy(regions = regions.map { region ->
        if (region.id != id) region else region.copy(x = x.coerceIn(0, width - region.width),
            y = y.coerceIn(0, height - region.height))
    })

    /** Enlarging the canvas preserves geometry; shrinking keeps each region inside it. */
    fun resizeCanvas(newWidth: Int, newHeight: Int): HudSceneLayout {
        require(newWidth in 240..8192 && newHeight in 120..8192)
        return copy(width = newWidth, height = newHeight, regions = regions.map { region ->
            val w = minOf(region.width, newWidth)
            val h = minOf(region.height, newHeight)
            region.copy(x = minOf(region.x, newWidth - w), y = minOf(region.y, newHeight - h), width = w, height = h)
        })
    }

    fun toJson() = buildJsonObject {
        put("width", width); put("height", height)
        put("enabled", enabled)
        put("regions", JsonArray(regions.map { region -> buildJsonObject {
            put("id", region.id); put("content", region.content.name)
            put("x", region.x); put("y", region.y); put("width", region.width); put("height", region.height)
            put("backgroundAlpha", region.backgroundAlpha); put("contentAlpha", region.contentAlpha)
            put("engineIndex", region.engineIndex)
            put("fields", region.fields?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull)
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
                    r.integer("width"), r.integer("height"), r.alpha("backgroundAlpha"), r.alpha("contentAlpha"), r.integer("engineIndex"),
                    r["fields"]?.takeUnless { it == JsonNull }?.jsonArray?.map { field -> field.jsonPrimitive.let {
                        require(it.isString); it.content
                    } })
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
