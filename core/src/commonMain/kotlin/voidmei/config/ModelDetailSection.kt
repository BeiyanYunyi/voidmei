package voidmei.config

/** Presentation only: model extraction and alert calculations never use this selection. */
enum class ModelDetailSection(val label: String) {
    WEIGHT("重量"), FLAPS("襟翼与起落架"), CONTROLS("舵效"), STALL("失速估算"),
    LOAD_LIMITS("过载限制"), FLIGHT_LIMITS("速度与迎角限制"), THERMAL("发动机耐热"),
    PISTON("活塞功率与曲线"), JET("喷气推力与曲线"), RAW("原始字段"), INERTIA("转动惯量"), WEP_FUEL("加力燃料")
}
