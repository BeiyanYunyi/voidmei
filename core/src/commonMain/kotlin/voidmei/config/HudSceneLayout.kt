package voidmei.config

import kotlinx.serialization.json.*

enum class HudRegionContent(val label: String) {
    FLIGHT("飞行读数"), ENGINE("发动机"), ATTITUDE("姿态"), MECHANIZATION("机械化"), ALERTS("告警"), MESSAGES("游戏消息"), MAP("地图对象"), CROSSHAIR("准星"), COMPASS("罗盘"), CONTROLS("操纵面")
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
    val visible: Boolean = true,
    val title: String = "",
    val readingColumns: Int? = null,
    val fontScale: Float? = null,
    val showFlightInstruments: Boolean = true,
    val messageLimit: Int = 5,
) {
    init {
        require(id.isNotBlank() && id.length <= 100 && id.none { it.isISOControl() })
        require(x in 0..8192 && y in 0..8192 && width in 80..8192 && height in 40..8192)
        require(backgroundAlpha.isFinite() && backgroundAlpha in 0f..1f)
        require(contentAlpha.isFinite() && contentAlpha in 0f..1f)
        require(engineIndex > 0)
        require(messageLimit in 1..20)
        require(fields == null || fields.all { it.isNotBlank() })
        require(title.length <= 80 && title.none { it.isISOControl() })
        require(readingColumns == null || readingColumns in 0..2)
        require(fontScale == null || (fontScale.isFinite() && fontScale in .75f..2f))
    }
}

data class HudSceneLayout(val width: Int, val height: Int, val regions: List<HudRegion>, val enabled: Boolean = true,
    val displayId: String? = null) {
    init {
        require(width in 240..8192 && height in 120..8192)
        require(displayId == null || displayId.isNotBlank())
        require(regions.size in 1..32 && regions.map { it.id }.distinct().size == regions.size)
        require(regions.all { it.x + it.width <= width && it.y + it.height <= height })
    }

    fun addRegion(content: HudRegionContent): HudSceneLayout {
        require(regions.size < 32)
        val id = (1..33).map { "region-$it" }.first { candidate -> regions.none { it.id == candidate } }
        val engine = (1..33).first { index -> regions.none { it.content == HudRegionContent.ENGINE && it.engineIndex == index } }
        val regionWidth = minOf(width, when (content) {
            HudRegionContent.CROSSHAIR -> 128
            HudRegionContent.COMPASS -> 240
            HudRegionContent.ATTITUDE -> 340
            else -> 440
        })
        val regionHeight = minOf(height, when (content) {
            HudRegionContent.FLIGHT, HudRegionContent.ENGINE -> 470
            HudRegionContent.ATTITUDE, HudRegionContent.MECHANIZATION -> 204
            HudRegionContent.ALERTS -> 180
            HudRegionContent.MESSAGES -> 300
            HudRegionContent.MAP -> 500
            HudRegionContent.CONTROLS -> 260
            HudRegionContent.CROSSHAIR -> 128
            HudRegionContent.COMPASS -> 240
        })
        // Stagger new regions so overlapping instances do not look like one unchanged region.
        val offset = 16 * (regions.size + 1)
        return copy(regions = regions + HudRegion(id, content, minOf(offset, width - regionWidth),
            minOf(offset, height - regionHeight), regionWidth, regionHeight,
            backgroundAlpha = if (content == HudRegionContent.CROSSHAIR) 0f else .5f, engineIndex = engine))
    }

    fun removeRegion(id: String): HudSceneLayout {
        val remaining = regions.filterNot { it.id == id }
        require(remaining.isNotEmpty()) { "Keep at least one HUD region" }
        return copy(regions = remaining)
    }

    fun duplicateRegion(id: String): HudSceneLayout {
        require(regions.size < 32)
        val layer = regions.indexOfFirst { it.id == id }
        require(layer >= 0) { "Unknown region" }
        val source = regions[layer]
        val newId = (1..33).map { "region-$it" }.first { candidate -> regions.none { it.id == candidate } }
        fun offset(position: Int, maximum: Int) = when {
            position + 16 <= maximum -> position + 16
            position >= 16 -> position - 16
            else -> maximum
        }
        val duplicate = source.copy(id = newId, x = offset(source.x, width - source.width),
            y = offset(source.y, height - source.height))
        return copy(regions = regions.toMutableList().apply { add(layer + 1, duplicate) })
    }

    /** Restore only the removed region, retaining edits made to the rest of the scene. */
    fun restoreRegion(region: HudRegion, layer: Int): HudSceneLayout {
        require(regions.size < 32)
        val id = if (regions.none { it.id == region.id }) region.id else
            (1..33).map { "region-$it" }.first { candidate -> regions.none { it.id == candidate } }
        val restoredWidth = minOf(region.width, width)
        val restoredHeight = minOf(region.height, height)
        val restored = region.copy(id = id, width = restoredWidth, height = restoredHeight,
            x = minOf(region.x, width - restoredWidth), y = minOf(region.y, height - restoredHeight))
        return copy(regions = regions.toMutableList().apply { add(layer.coerceIn(0, size), restored) })
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

    fun resizeRegion(id: String, width: Int, height: Int): HudSceneLayout = copy(regions = regions.map { region ->
        if (region.id != id) region else region.copy(width = width.coerceIn(80, this.width - region.x),
            height = height.coerceIn(40, this.height - region.y))
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
        put("displayId", displayId?.let(::JsonPrimitive) ?: JsonNull)
        put("regions", JsonArray(regions.map { region -> buildJsonObject {
            put("id", region.id); put("content", region.content.name)
            put("x", region.x); put("y", region.y); put("width", region.width); put("height", region.height)
            put("backgroundAlpha", region.backgroundAlpha); put("contentAlpha", region.contentAlpha)
            put("engineIndex", region.engineIndex)
            put("fields", region.fields?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull)
            put("visible", region.visible)
            put("title", region.title)
            put("readingColumns", region.readingColumns?.let(::JsonPrimitive) ?: JsonNull)
            put("fontScale", region.fontScale?.let(::JsonPrimitive) ?: JsonNull)
            put("showFlightInstruments", region.showFlightInstruments)
            put("messageLimit", region.messageLimit)
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
                    } }, r["visible"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: true,
                    r["title"]?.jsonPrimitive?.let { require(it.isString); it.content } ?: "",
                    r["readingColumns"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(!it.isString); it.int },
                    r["fontScale"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(!it.isString); it.float },
                    r["showFlightInstruments"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: true,
                    r["messageLimit"]?.jsonPrimitive?.let { require(!it.isString); it.int } ?: 5)
            }, root["enabled"]?.jsonPrimitive?.let { require(!it.isString); it.boolean } ?: true,
                root["displayId"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(it.isString); it.content })
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
