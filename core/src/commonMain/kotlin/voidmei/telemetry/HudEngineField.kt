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
    BOOST_PSI("boost_psi", "增压（相对1atm）", "psi", 1),
    HEAT_BUDGET("heat_budget", "耐热时估计", "s", 1),
    FM_POWER_PERCENT("fm_power_percent", "动力量（FM峰值）", "%", 1),
    THRUST_POWER("thrust_power", "推进功率", "kW", 1),
    PROPULSIVE_EFFICIENCY("propulsive_efficiency", "推进效率估计", "%", 1);

    fun value(engine: Engine, tasKmh: Double? = null): Double? = when (this) {
        THROTTLE -> engine.throttlePercent?.takeIf { it >= 0 }
        RPM -> engine.rpm?.takeIf { it >= 0 }
        POWER -> engine.powerHp
        THRUST -> engine.thrustKgf
        WATER_TEMPERATURE -> engine.waterTemperatureC
        OIL_TEMPERATURE -> engine.oilTemperatureC
        RPM_CONTROL -> engine.rpmControlPercent?.takeIf { it >= 0 }
        MIXTURE -> engine.mixturePercent?.takeIf { it >= 0 }
        RADIATOR -> engine.radiatorPercent?.takeIf { it >= 0 }
        OIL_RADIATOR -> engine.oilRadiatorPercent?.takeIf { it >= 0 }
        COMPRESSOR -> engine.compressorStage?.takeIf { it >= 1 && it % 1.0 == 0.0 }
        MAGNETO -> engine.magneto
        MANIFOLD -> ManifoldPressureUnit.ATM.fromAtm(engine.manifoldPressureAtm)
        MANIFOLD_INHG -> ManifoldPressureUnit.INHG.fromAtm(engine.manifoldPressureAtm)
        BOOST_PSI -> ManifoldPressureUnit.BOOST_PSI.fromAtm(engine.manifoldPressureAtm)
        PITCH -> engine.propellerPitchDeg
        EFFICIENCY -> engine.efficiencyPercent
        HEAT_BUDGET -> null // Supplied as an uncertainty interval from the matching thermal observation.
        FM_POWER_PERCENT -> null // Requires the selected aircraft's per-engine reference.
        THRUST_POWER -> {
            val speed = tasKmh?.takeIf { it.isFinite() && it >= 0 }?.div(3.6)
            val thrust = engine.thrustKgf?.takeIf { it.isFinite() && it >= 0 }
            if (speed != null && thrust != null) thrust * FlightCalculator.G * speed / 1000 else null
        }
        PROPULSIVE_EFFICIENCY -> {
            val shaft = engine.powerHp?.takeIf { it.isFinite() && it > 0 }
            val useful = THRUST_POWER.value(engine, tasKmh)
            // Match the whole-aircraft estimate's legacy 735 W/hp reference.
            if (shaft != null && useful != null) useful / shaft / .735 * 100 else null
        }
    }?.takeIf { it.isFinite() }

    companion object {
        val defaults = entries.filterNot { it == MANIFOLD_INHG || it == BOOST_PSI || it == HEAT_BUDGET || it == FM_POWER_PERCENT || it == THRUST_POWER || it == PROPULSIVE_EFFICIENCY }.map { it.id }
        fun selected(ids: List<String>) = ids.distinct().mapNotNull { id -> entries.find { it.id == id } }
    }
}
