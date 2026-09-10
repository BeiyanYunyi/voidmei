package voidmei.telemetry

import kotlinx.serialization.json.*
import kotlin.math.hypot
import kotlin.math.abs

data class MapPoint(val x: Double, val y: Double)
data class MapBounds(val minimum: MapPoint, val maximum: MapPoint, val generation: Int?,
    val gridSteps: MapPoint?, val gridZero: MapPoint?) {
    val widthM get() = maximum.x - minimum.x
    val heightM get() = maximum.y - minimum.y
}
data class MapObject(val type: String?, val icon: String?, val colorRgb: Int?, val position: MapPoint?,
    val direction: MapPoint?, val start: MapPoint?, val end: MapPoint?, val blink: Int?) {
    val unitDirection: MapPoint? get() {
        val vector = direction ?: return null
        if (!vector.x.isFinite() || !vector.y.isFinite()) return null
        val scale = maxOf(abs(vector.x), abs(vector.y))
        if (scale == 0.0) return null
        val x = vector.x / scale
        val y = vector.y / scale
        val length = hypot(x, y)
        return MapPoint(x / length, y / length)
    }
}
data class MapSnapshot(val bounds: MapBounds, val objects: List<MapObject>) {
    val player get() = objects.singleOrNull { it.icon.equals("Player", true) }
    fun distanceFromPlayerM(target: MapObject): Double? {
        val origin = player?.position ?: return null
        val point = target.position ?: return null
        return hypot((point.x - origin.x) * bounds.widthM, (point.y - origin.y) * bounds.heightM).takeIf { it.isFinite() }
    }
}

object MapTelemetryParser {
    private fun number(value: JsonElement?): Double? = (value as? JsonPrimitive)?.takeUnless { it.isString }
        ?.doubleOrNull?.takeIf { it.isFinite() && it != -65535.0 }
    private fun text(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun pair(value: JsonElement?): MapPoint? {
        val array = value as? JsonArray ?: return null
        if (array.size != 2) return null
        return MapPoint(number(array[0]) ?: return null, number(array[1]) ?: return null)
    }
    fun info(text: String): MapBounds? {
        require(text.length <= 1024 * 1024) { "地图信息过大" }
        val root = Json.parseToJsonElement(text).jsonObject
        if ((root["valid"] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == true } != true) return null
        val minimum = pair(root["map_min"]) ?: return null
        val maximum = pair(root["map_max"]) ?: return null
        val bounds = MapBounds(minimum, maximum, (root["map_generation"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull,
            pair(root["grid_steps"]), pair(root["grid_zero"]))
        return bounds.takeIf { it.widthM.isFinite() && it.heightM.isFinite() && it.widthM > 0 && it.heightM > 0 }
    }
    fun objects(text: String): List<MapObject> {
        require(text.length <= 8 * 1024 * 1024) { "地图对象数据过大" }
        val array = Json.parseToJsonElement(text).jsonArray
        require(array.size <= 10000) { "地图对象过多" }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun point(x: String, y: String) = number(obj[x])?.let { a -> number(obj[y])?.let { b -> MapPoint(a, b) } }
            val rgb = (obj["color[]"] as? JsonArray)?.takeIf { it.size == 3 }?.map {
                (it as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            }?.takeIf { it.all { n -> n != null && n in 0..255 } }?.let { (it[0]!! shl 16) or (it[1]!! shl 8) or it[2]!! }
                ?: text(obj["color"])?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.drop(1)?.toInt(16)
            MapObject(text(obj["type"]), text(obj["icon"]), rgb, point("x", "y"), point("dx", "dy"),
                point("sx", "sy"), point("ex", "ey"), (obj["blink"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull)
        }
    }
}
