package voidmei.config

import kotlinx.serialization.json.*

/** Null preserves the existing text style; a null unit weight follows the number. */
data class ReadingTextWeights(val label: Int? = null, val number: Int? = null, val unit: Int? = null) {
    init { require(listOf(label, number, unit).all { it == null || it == 400 || it == 700 }) }
    fun toJson() = buildJsonObject {
        put("label", label?.let(::JsonPrimitive) ?: JsonNull)
        put("number", number?.let(::JsonPrimitive) ?: JsonNull)
        put("unit", unit?.let(::JsonPrimitive) ?: JsonNull)
    }
    companion object {
        val legacyFlight = ReadingTextWeights(700, 700, 400)
        fun fromJson(value: JsonElement): ReadingTextWeights {
            val root = value.jsonObject
            fun weight(key: String) = root[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { require(!it.isString); it.int }
            return ReadingTextWeights(weight("label"), weight("number"), weight("unit"))
        }
    }
}
