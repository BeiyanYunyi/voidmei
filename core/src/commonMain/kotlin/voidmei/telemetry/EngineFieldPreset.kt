package voidmei.telemetry

/** Ordered per-engine selections; aircraft-wide fuel and mass remain flight readings. */
enum class EngineFieldPreset(val label: String, val fields: List<String>) {
    POWER("动力读数", listOf(HudEngineField.POWER, HudEngineField.THRUST, HudEngineField.RPM,
        HudEngineField.PITCH, HudEngineField.EFFICIENCY, HudEngineField.MANIFOLD,
        HudEngineField.FM_POWER_PERCENT, HudEngineField.WATER_TEMPERATURE,
        HudEngineField.OIL_TEMPERATURE, HudEngineField.HEAT_BUDGET).map { it.id }),
    CONTROLS("引擎控制", listOf(HudEngineField.THROTTLE, HudEngineField.RPM_CONTROL,
        HudEngineField.FM_POWER_PERCENT, HudEngineField.MIXTURE, HudEngineField.RADIATOR,
        HudEngineField.OIL_RADIATOR, HudEngineField.COMPRESSOR).map { it.id }),
    DEFAULT("默认字段", HudEngineField.defaults);
}
