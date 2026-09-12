package voidmei.config

import kotlinx.serialization.json.*

/** Base sp sizes; system and region text scaling still apply. */
data class ReadingTextSizes(val label: Float = 13f, val number: Float = 14f, val unit: Float = 14f) {
    init { require(listOf(label, number, unit).all { it.isFinite() && it in 6f..64f }) }
    fun toJson() = buildJsonObject { put("label", label); put("number", number); put("unit", unit) }
    companion object {
        fun fromJson(value: JsonElement): ReadingTextSizes {
            val root = value.jsonObject
            fun size(key: String) = root.getValue(key).jsonPrimitive.let { require(!it.isString); it.float }
            return ReadingTextSizes(size("label"), size("number"), size("unit"))
        }
        fun fromLegacyOffset(offset: Int): ReadingTextSizes {
            require(offset in -6..20)
            val number = 24 + offset
            val half = (number + 1) / 2
            return ReadingTextSizes(half.toFloat(), number.toFloat(), half.toFloat())
        }
    }
}
