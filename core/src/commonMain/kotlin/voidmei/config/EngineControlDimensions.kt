package voidmei.config

import kotlinx.serialization.json.*

/** Logical dp dimensions; a region's null value keeps the original adaptive layout. */
data class EngineControlDimensions(val lengthDp: Int = 112, val thicknessDp: Int = 18) {
    init { require(lengthDp in 48..512 && thicknessDp in 8..48) }
    fun toJson() = buildJsonObject { put("lengthDp", lengthDp); put("thicknessDp", thicknessDp) }
    companion object {
        fun fromJson(value: JsonElement): EngineControlDimensions {
            val root = value.jsonObject
            fun number(key: String) = root.getValue(key).jsonPrimitive.let { require(!it.isString); it.int }
            return EngineControlDimensions(number("lengthDp"), number("thicknessDp"))
        }
    }
}
