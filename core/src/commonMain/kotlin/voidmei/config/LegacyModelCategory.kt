package voidmei.config

/** Import presentation intent only; never import obsolete numeric calculations. */
enum class LegacyModelCategory(val target: String, val label: String, val section: ModelDetailSection, val note: String) {
    WEIGHT("showWeight", "重量", ModelDetailSection.WEIGHT, "模型空重与最大燃油容量。"),
    SPEED("showCritSpeed", "临界速度", ModelDetailSection.FLIGHT_LIMITS, "控制新版速度与迎角限制；不迁移旧无效临界速度数值，不更改独立失速估算分类。"),
    LOAD("showGLoadLimits", "过载极限", ModelDetailSection.LOAD_LIMITS, "显示新版当前燃油／后掠下的基础质量估算，不复用旧满油／半油冗余系数。"),
    FLAPS("showFlapLimits", "襟翼限速", ModelDetailSection.FLAPS, "新版分类包含有效襟翼限速数据点表、当前可用襟翼开度和起落架限速。"),
    CONTROLS("showControlEffectiveness", "三舵效能", ModelDetailSection.CONTROLS, "显示三舵衰减速度与 PowerLoss 原始系数（旧称锁舵因数）；不表示实时舵效或锁舵百分比。")
}
