package voidmei.config

import voidmei.telemetry.EngineFieldPreset

/** Preview a new region without modifying the scene or guessing a second engine exists. */
fun HudSceneLayout.legacyEngineRegion(key: String): HudRegion {
    require(key == "engineInfoSwitch" || key == "enableEngineControl")
    val power = key == "engineInfoSwitch"
    val prefix = if (power) "legacy-power" else "legacy-engine-controls"
    val id = (listOf(prefix) + (1..33).map { "$prefix-$it" }).first { candidate -> regions.none { it.id == candidate } }
    val base = addRegion(HudRegionContent.ENGINE).regions.last()
    return base.copy(id = id,
        x = if (power) base.x else (base.x + base.width + 16).coerceAtMost(width - base.width),
        y = if (power || width >= base.width * 2 + 16) base.y else (base.y + 32).coerceAtMost(height - base.height),
        title = if (power) "动力信息" else "引擎控制", engineIndex = 1,
        showEngineReadings = power, engineControlsLayout = if (power) EngineControlsLayout.HORIZONTAL else EngineControlsLayout.MIXED,
        fields = (if (power) EngineFieldPreset.POWER else EngineFieldPreset.CONTROLS).fields)
}
