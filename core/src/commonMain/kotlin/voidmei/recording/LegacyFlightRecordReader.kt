package voidmei.recording

import kotlin.math.roundToLong

/** Adapter for src/parser/FlightLog.java and the repository's Chinese lang/cur.properties. */
object LegacyFlightRecordReader {
    val header = "时间/s,节流阀/%,表　速/kph,真空速/kph,马赫数/Ma,高　度/m,温　度/℃,油　温/℃,爬升率/m/s,ＳＥＰ*/m/s,过　载/G,滚转率/deg/s,功　率/hp,桨效率/%,实功率*/hp,转　速/rpm,推　力/kg,加速度*/m/s^2,桨　距/%,桨距角/deg,散热器/%,混合比/%,增压器/档,磁电机/档,进气压/ata,襟　翼/%,升降舵/%,滚转舵/%,方向舵/%,攻　角/deg,侧滑角/deg,"
    private val mapping = linkedMapOf(2 to "ias_kmh", 3 to "tas_kmh", 4 to "mach", 5 to "altitude_m",
        8 to "vertical_speed_mps", 9 to "sep_mps", 10 to "load_g", 12 to "total_power_hp",
        16 to "total_thrust_kgf", 17 to "acceleration_mps2", 25 to "flaps_percent", 26 to "elevator_percent",
        27 to "aileron_percent", 28 to "rudder_percent", 29 to "aoa_deg", 30 to "sideslip_deg")

    val notes = listOf(
        "旧版表头时间标为秒，但写入代码使用分钟；导入已按分钟转换。",
        "旧文件不含机型和 UTC 时间；仅转换已核对单位的飞行字段，未导入发动机明细。")

    fun analyze(text: String, checkActive: () -> Unit = {}): FlightRecordAnalysis =
        FlightRecordPlots.analyze(normalize(text, checkActive), checkActive = checkActive).copy(notes = notes)

    fun normalize(text: String, checkActive: () -> Unit = {}): String {
        checkActive()
        require(text.length <= FlightRecordReader.MAX_BYTES) { "旧记录超过 64 MiB 限制" }
        val rows = FlightRecordReader.rows(text.removePrefix("\uFEFF"), checkActive).iterator()
        require(rows.hasNext() && rows.next() == header.split(',')) { "不是受支持的旧版中文飞行 CSV 表头" }
        var samples = 0
        val normalized = buildString {
            append("sample_id,utc_epoch_ms,elapsed_ms,aircraft,")
            append(mapping.values.joinToString(",")); append('\n')
            while (rows.hasNext()) {
                checkActive()
                val row = rows.next()
                require(row.size == 32 && row.last().isEmpty()) { "旧记录第 ${samples + 2} 行列数不符" }
                val minutes = requireNotNull(row[0].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }) { "旧记录时间无效" }
                val milliseconds = minutes * 60000
                require(milliseconds.isFinite() && milliseconds < Long.MAX_VALUE.toDouble()) { "旧记录时间溢出" }
                append(samples); append(",,"); append(milliseconds.roundToLong()); append(",,")
                append(mapping.keys.joinToString(",") { index ->
                    val value = row[index]
                    if (value == "-" || value.isEmpty()) "" else {
                        require(value.toDoubleOrNull()?.isFinite() == true) { "旧记录第 ${samples + 2} 行 ${mapping[index]} 无效" }
                        value
                    }
                })
                append('\n')
                samples++
                require(samples <= 1_000_000 && length <= FlightRecordReader.MAX_BYTES) { "转换后的记录超出读取限制" }
            }
        }
        return normalized
    }
}
