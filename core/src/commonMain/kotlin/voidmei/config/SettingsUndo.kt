package voidmei.config

import kotlinx.serialization.json.*

/** One action's changes, compared at persisted top-level setting boundaries. */
class SettingsUndo private constructor(
    private val before: JsonObject,
    private val after: JsonObject,
    private val changed: Set<String>,
) {
    data class Result(val settings: AppSettings, val restoredKeys: Set<String>, val skippedKeys: Set<String>)

    fun preview(current: AppSettings): Result {
        val currentJson = encode(current)
        val restorable = changed.filterTo(linkedSetOf()) { currentJson[it] == after[it] }
        val restored = currentJson.toMutableMap()
        restorable.forEach { key -> restored[key] = before.getValue(key) }
        return Result(if (restorable.isEmpty()) current else SettingsJson.decode(JsonObject(restored).toString()),
            restorable, changed - restorable)
    }

    companion object {
        private fun encode(settings: AppSettings) = Json.parseToJsonElement(SettingsJson.encode(settings)).jsonObject

        fun capture(before: AppSettings, after: AppSettings): SettingsUndo? {
            val original = encode(before)
            val applied = encode(after)
            val changed = original.keys.filterTo(linkedSetOf()) { original[it] != applied[it] }
            return if (changed.isEmpty()) null else SettingsUndo(original, applied, changed)
        }
    }
}
