package voidmei.fm

data class LoadLimits(val minimumG: Double, val maximumG: Double)

/** Legacy basic-mass estimate; payload, modifications and consumed oil/nitro are not measured. */
data class StructuralLoadModel(val negativeForce: Double, val positiveForce: Double, val basicMassKg: Double) {
    init {
        require(negativeForce.isFinite() && negativeForce < 0)
        require(positiveForce.isFinite() && positiveForce > 0)
        require(basicMassKg.isFinite() && basicMassKg > 0)
    }

    fun limits(fuelKg: Double?): LoadLimits? {
        if (fuelKg == null || !fuelKg.isFinite() || fuelKg < 0) return null
        val mass = basicMassKg + fuelKg
        if (!mass.isFinite()) return null
        val negative = 1.2 * (2 * negativeForce / (9.80 * mass) + 1)
        val positive = 1.2 * (2 * positiveForce / (9.80 * mass) - 1)
        if (!negative.isFinite() || !positive.isFinite() || negative >= 0 || positive <= 0) return null
        return LoadLimits(negative, positive)
    }
}
