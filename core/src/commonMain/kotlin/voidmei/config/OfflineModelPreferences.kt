package voidmei.config

import kotlinx.serialization.json.*

/** Persist identifiers, never a stale model snapshot or an automatically opened window. */
data class OfflineModelPreferences(
    val dataRoot: String? = null,
    val aircraft: String? = null,
    val baselineAircraft: String? = null,
) {
    init {
        require(dataRoot == null || (dataRoot.isNotBlank() && dataRoot.length <= 4096 && dataRoot.none { it.isISOControl() }))
        require(listOf(aircraft, baselineAircraft).all { it == null || (it.length in 1..128 && Regex("[a-z0-9_-]+").matches(it)) })
    }

    internal fun toJson() = buildJsonObject {
        dataRoot?.let { put("dataRoot", it) }
        aircraft?.let { put("aircraft", it) }
        baselineAircraft?.let { put("baselineAircraft", it) }
    }

    companion object {
        internal fun fromJson(value: JsonElement): OfflineModelPreferences {
            val root = value.jsonObject
            fun text(key: String) = root[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.let {
                require(it.isString); it.content
            }
            return OfflineModelPreferences(text("dataRoot"), text("aircraft"), text("baselineAircraft"))
        }
    }
}
