package voidmei.telemetry

/** Stable IDs for the independently selectable per-engine HUD readings. */
enum class HudEngineField(val id: String, val label: String, val unit: String, val decimals: Int = 0) {
    THROTTLE("throttle", "油门", "%"), RPM("rpm", "转速", "RPM"),
    POWER("power", "功率", "hp"), THRUST("thrust", "推力", "kgf"),
    WATER_TEMPERATURE("water_temperature", "水温", "°C", 1), OIL_TEMPERATURE("oil_temperature", "油温", "°C", 1),
    RPM_CONTROL("rpm_control", "转速控制", "%"), MIXTURE("mixture", "混合比", "%"),
    RADIATOR("radiator", "水散热器", "%"), OIL_RADIATOR("oil_radiator", "油散热器", "%"),
    COMPRESSOR("compressor", "增压器档位", ""), MAGNETO("magneto", "磁电机", ""),
    MANIFOLD("manifold", "进气压力", "atm", 2), PITCH("pitch", "桨叶角", "°", 1),
    EFFICIENCY("efficiency", "效率", "%");

    fun value(engine: Engine): Double? = when (this) {
        THROTTLE -> engine.throttlePercent
        RPM -> engine.rpm
        POWER -> engine.powerHp
        THRUST -> engine.thrustKgf
        WATER_TEMPERATURE -> engine.waterTemperatureC
        OIL_TEMPERATURE -> engine.oilTemperatureC
        RPM_CONTROL -> engine.rpmControlPercent
        MIXTURE -> engine.mixturePercent
        RADIATOR -> engine.radiatorPercent
        OIL_RADIATOR -> engine.oilRadiatorPercent
        COMPRESSOR -> engine.compressorStage
        MAGNETO -> engine.magneto
        MANIFOLD -> engine.manifoldPressureAtm
        PITCH -> engine.propellerPitchDeg
        EFFICIENCY -> engine.efficiencyPercent
    }?.takeIf { it.isFinite() }

    companion object {
        val defaults = entries.map { it.id }
        fun selected(ids: List<String>) = ids.distinct().mapNotNull { id -> entries.find { it.id == id } }
    }
}
