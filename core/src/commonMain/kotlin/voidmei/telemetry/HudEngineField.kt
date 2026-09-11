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
    EFFICIENCY("efficiency", "效率", "%"),
    MANIFOLD_INHG("manifold_inhg", "进气压力（inHg）", "inHg", 1),
    BOOST_PSI("boost_psi", "增压（相对1atm）", "psi", 1);

    fun value(engine: Engine): Double? = when (this) {
        THROTTLE -> engine.throttlePercent?.takeIf { it >= 0 }
        RPM -> engine.rpm?.takeIf { it >= 0 }
        POWER -> engine.powerHp
        THRUST -> engine.thrustKgf
        WATER_TEMPERATURE -> engine.waterTemperatureC
        OIL_TEMPERATURE -> engine.oilTemperatureC
        RPM_CONTROL -> engine.rpmControlPercent?.takeIf { it >= 0 }
        MIXTURE -> engine.mixturePercent?.takeIf { it >= 0 }
        RADIATOR -> engine.radiatorPercent
        OIL_RADIATOR -> engine.oilRadiatorPercent
        COMPRESSOR -> engine.compressorStage
        MAGNETO -> engine.magneto
        MANIFOLD -> ManifoldPressureUnit.ATM.fromAtm(engine.manifoldPressureAtm)
        MANIFOLD_INHG -> ManifoldPressureUnit.INHG.fromAtm(engine.manifoldPressureAtm)
        BOOST_PSI -> ManifoldPressureUnit.BOOST_PSI.fromAtm(engine.manifoldPressureAtm)
        PITCH -> engine.propellerPitchDeg
        EFFICIENCY -> engine.efficiencyPercent
    }?.takeIf { it.isFinite() }

    companion object {
        val defaults = entries.filterNot { it == MANIFOLD_INHG || it == BOOST_PSI }.map { it.id }
        fun selected(ids: List<String>) = ids.distinct().mapNotNull { id -> entries.find { it.id == id } }
    }
}
