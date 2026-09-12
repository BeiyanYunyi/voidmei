package voidmei.recording

/** Flight CSV metrics available for summary and historical plotting. */
enum class RecordedField(val id: String, val label: String) {
    IAS("ias_kmh", "IAS (km/h)"), TAS("tas_kmh", "TAS (km/h)"), ALTITUDE("altitude_m", "高度 (m)"),
    LOAD("load_g", "载荷 (g)"), AOA("aoa_deg", "迎角 (°)"), FUEL("fuel_kg", "燃油 (kg)"),
    SEP("sep_mps", "SEP (m/s)"), CLIMB("vertical_speed_mps", "垂直速度 (m/s)"),
    RADAR("radio_altitude_raw", "雷达高度原值（仪表单位）"),
    MACH("mach", "马赫数"), FUEL_CAPACITY("fuel_capacity_kg", "燃油容量 (kg)"),
    GEAR("gear_percent", "起落架 (%)"), FLAPS("flaps_percent", "襟翼 (%)"), BRAKE("airbrake_percent", "减速板 (%)"),
    AILERON("aileron_percent", "副翼 (%)"), ELEVATOR("elevator_percent", "升降舵 (%)"), RUDDER("rudder_percent", "方向舵 (%)"),
    ROLL("roll_deg", "滚转角 (°)"), PITCH("pitch_deg", "俯仰仪表原值 (°，抬头为负)"), HEADING("heading_deg", "航向 (°)"),
    SWEEP("wing_sweep_ratio", "后掠比例"), ENERGY("energy_height_m", "能量高度 (m)"),
    ACCELERATION("acceleration_mps2", "加速度 (m/s²)"), TURN_RADIUS("turn_radius_m", "转弯半径估计 (m)"),
    TURN_RATE("turn_rate_degps", "转弯率估计 (°/s)"), FUEL_RATE("fuel_kg_min", "燃油消耗 (kg/min)"),
    ENDURANCE("endurance_s", "续航估计 (s)"), POWER("total_power_hp", "总功率 (hp)"),
    THRUST("total_thrust_kgf", "总推力 (kgf)"), THRUST_POWER("thrust_power_kw", "推力功率 (kW)"),
    FUEL_PRESSURE("fuel_pressure_raw", "燃油压力原值（仪表单位）"),
    OIL_PRESSURE("oil_pressure_raw", "滑油压力原值（仪表单位）"),
    SIDESLIP("sideslip_deg", "侧滑角 (°)"), ROLL_RATE("roll_rate_degps", "滚转角速度 (°/s)"),
    WATER_TEMPERATURE_RAW("water_temperature_raw", "水温仪表原值（仪表单位）"),
    HEAD_TEMPERATURE_RAW("head_temperature_raw", "缸温仪表原值（仪表单位）"),
    OIL_TEMPERATURE_RAW("oil_temperature_raw", "油温仪表原值（仪表单位）"),
    ALTIMETER_RAW("altimeter_raw", "高度仪表原值（仪表单位）"),
    ENGINE_RESPONSE("engine_response_percent_per_s", "动力量响应（百分点/s）"),
    BOOSTER_FUEL("booster_fuel_kg", "助推燃料通道 1 (kg)"),
    BOOSTER_FUEL_CAPACITY("booster_fuel_capacity_kg", "助推燃料通道 1 容量 (kg)"),
    WEP_FUEL_UPPER("wep_fuel_upper_kg", "WEP 燃料上限 (kg)"),
    WEP_TIME_UPPER("wep_time_upper_s", "WEP 续航上限 (s)");

    companion object { val quick = entries.take(9) }
}
