package voidmei.config

/** Import presentation intent only; never import obsolete numeric calculations. */
enum class LegacyModelCategory(val target: String, val label: String, val section: ModelDetailSection, val note: String) {
    WEIGHT("showWeight", "重量", ModelDetailSection.WEIGHT, "模型空重与最大燃油容量。"),
    SPEED("showCritSpeed", "临界速度", ModelDetailSection.FLIGHT_LIMITS, "控制新版速度与迎角限制；不迁移旧无效临界速度数值，不更改独立失速估算分类。"),
    LOAD("showGLoadLimits", "过载极限", ModelDetailSection.LOAD_LIMITS, "显示新版当前燃油／后掠下的基础质量估算，不复用旧满油／半油冗余系数。"),
    FLAPS("showFlapLimits", "襟翼限速", ModelDetailSection.FLAPS, "新版分类包含有效襟翼限速数据点表、当前可用襟翼开度和起落架限速。"),
    CONTROLS("showControlEffectiveness", "三舵效能", ModelDetailSection.CONTROLS, "显示三舵衰减速度与 PowerLoss 原始系数（旧称锁舵因数）；不表示实时舵效或锁舵百分比。"),
    INERTIA("showInertia", "转动惯量", ModelDetailSection.INERTIA, "显示 MomentOfInertia 的俯仰／滚转／偏航原值，沿用旧版轴顺序，不换算单位。"),
    WEP_FUEL("showNitro", "加力信息", ModelDetailSection.WEP_FUEL, "显示共享加力燃料容量、逐发动机消耗率及全发动机持续加力理论时限，不是飞行剩余时间。"),
    THERMAL_RECOVERY("showHeatRecovery", "耐热恢复", ModelDetailSection.THERMAL_RECOVERY, "显示逐发动机有效档位的 WorkTime / RecoverTime 算术平均；不沿用旧固定分母，不表示实时恢复速度。"),
    CLEAN_PART("showNoFlapsWing", "无襟翼", ModelDetailSection.CLEAN_PART, "按来源显示器件原始参数；迎角未扣安装角，别名与后掠配置分开展示。"),
    FULL_PART("showFullFlapsWing", "满襟翼", ModelDetailSection.FULL_PART, "按来源显示器件原始参数；迎角未扣安装角，别名与后掠配置分开展示。"),
    FUSELAGE_PART("showFuselage", "机身", ModelDetailSection.FUSELAGE_PART, "按来源显示器件原始参数；迎角未扣安装角，别名与后掠配置分开展示。"),
    FIN_PART("showFin", "Fin（旧垂尾开关）", ModelDetailSection.FIN_PART, "按来源显示器件原始参数；迎角未扣安装角，别名与后掠配置分开展示。"),
    STAB_PART("showStab", "Stab（旧平尾开关）", ModelDetailSection.STAB_PART, "按来源显示器件原始参数；迎角未扣安装角，别名与后掠配置分开展示。")
}
