package voidmei.fm

data class ModelDifference(val label: String, val unit: String, val baseline: Double?, val current: Double?) {
    val delta: Double? get() = if (baseline != null && current != null) (current - baseline).takeIf { it.isFinite() } else null
}

object ModelComparison {
    fun compare(baseline: FlightModelParameters, current: FlightModelParameters,
        sweep: Double = 0.0, flaps: Double = 0.0, fuelFraction: Double = 0.0): List<ModelDifference> {
        require(sweep.isFinite() && sweep in 0.0..1.0 && flaps.isFinite() && flaps in 0.0..100.0)
        require(fuelFraction.isFinite() && fuelFraction in 0.0..1.0)
        fun fuel(model: FlightModelParameters): Double? = if (fuelFraction == 0.0) 0.0 else
            model.maximumFuelMassKg?.takeIf { it.isFinite() && it >= 0 }?.times(fuelFraction)
        val leftFuel = fuel(baseline)
        val rightFuel = fuel(current)
        val leftLoad = baseline.loadLimits(leftFuel, sweep)
        val rightLoad = current.loadLimits(rightFuel, sweep)
        val left = baseline.limits(sweep, flaps)
        val right = current.limits(sweep, flaps)
        fun row(label: String, unit: String, a: Double?, b: Double?) =
            ModelDifference(label, unit, a?.takeIf { it.isFinite() }, b?.takeIf { it.isFinite() })
        return listOf(
            row("空重", "kg", baseline.emptyMassKg, current.emptyMassKg),
            row("燃油容量", "kg", baseline.maximumFuelMassKg, current.maximumFuelMassKg),
            row("比较燃油量", "kg", leftFuel, rightFuel),
            row("1 G 失速 IAS 估算", "km/h", baseline.stallSpeed?.speedKmh(leftFuel, flaps, sweep), current.stallSpeed?.speedKmh(rightFuel, flaps, sweep)),
            row("负过载限制估算", "G", leftLoad?.minimumG, rightLoad?.minimumG),
            row("正过载限制估算", "G", leftLoad?.maximumG, rightLoad?.maximumG),
            row("VNE", "km/h", left?.vneKmh, right?.vneKmh),
            row("马赫限制", "Mach", left?.maxMach, right?.maxMach),
            row("迎角下限", "°", left?.minAngleOfAttackDeg, right?.minAngleOfAttackDeg),
            row("迎角上限", "°", left?.maxAngleOfAttackDeg, right?.maxAngleOfAttackDeg),
            row("起落架速度限制", "km/h", baseline.gearLimitKmh, current.gearLimitKmh),
            row("襟翼速度限制", "km/h", baseline.flapLimits?.speedAt(flaps), current.flapLimits?.speedAt(flaps)),
            row("副翼舵效衰减速度", "km/h", baseline.controlSpeeds.aileronKmh, current.controlSpeeds.aileronKmh),
            row("升降舵舵效衰减速度", "km/h", baseline.controlSpeeds.elevatorKmh, current.controlSpeeds.elevatorKmh),
            row("方向舵舵效衰减速度", "km/h", baseline.controlSpeeds.rudderKmh, current.controlSpeeds.rudderKmh),
        )
    }
}
