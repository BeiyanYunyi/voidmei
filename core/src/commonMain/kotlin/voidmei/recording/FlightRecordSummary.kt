package voidmei.recording

data class RecordedRange(val count: Int, val minimum: Double, val maximum: Double)
data class FlightRecordSummary(val aircraft: String, val samples: Int, val spanMs: Long,
    val startEpochMs: Long?, val endEpochMs: Long?, val ranges: Map<String, RecordedRange>)

/** Reads the KMP flight CSV schema, including reordered columns and quoted aircraft names. */
object FlightRecordReader {
    const val MAX_BYTES = 64 * 1024 * 1024
    fun summarize(text: String, metricFields: List<String> = FlightCsv.flightHeader.split(',').drop(4), checkActive: () -> Unit = {}): FlightRecordSummary {
        checkActive()
        require(text.length <= MAX_BYTES) { "记录超过 64 MiB 限制" }
        val rows = rows(text.removePrefix("\uFEFF"), checkActive).iterator()
        require(rows.hasNext()) { "记录为空" }
        val header = rows.next()
        require(header.distinct().size == header.size && header.none { it.isEmpty() }) { "CSV 表头包含重复或空列名" }
        val indices = header.withIndex().associate { it.value to it.index }
        val metadata = listOf("sample_id", "utc_epoch_ms", "elapsed_ms", "aircraft")
        require(metadata.all { it in indices }) { "不是 Kotlin 飞行记录 CSV（缺少必要列）" }
        val metrics = metricFields.distinct().filter { it in indices }
        require(metrics.isNotEmpty()) { "记录中没有可识别的遥测列" }
        val ranges = mutableMapOf<String, RecordedRange>()
        var aircraft: String? = null
        var count = 0
        var firstElapsed = 0L
        var previousElapsed = 0L
        var previousId = -1L
        var startEpoch: Long? = null
        var endEpoch: Long? = null
        while (rows.hasNext()) {
            checkActive()
            val row = rows.next()
            require(row.size == header.size) { "第 ${count + 2} 条记录列数不符" }
            fun value(key: String) = row[indices.getValue(key)]
            fun integer(key: String) = requireNotNull(value(key).toLongOrNull()?.takeIf { it >= 0 }) { "第 ${count + 2} 条记录 $key 无效" }
            val id = integer("sample_id")
            val elapsed = integer("elapsed_ms")
            val epoch = if (value("utc_epoch_ms").isEmpty()) null else integer("utc_epoch_ms")
            require(id > previousId && (count == 0 || elapsed >= previousElapsed)) { "采样编号或经过时间倒退/重复" }
            val name = value("aircraft")
            require(aircraft == null || aircraft == name) { "文件包含多个机型，请分别分析每段飞行" }
            aircraft = name
            if (count == 0) { firstElapsed = elapsed; startEpoch = epoch }
            previousId = id; previousElapsed = elapsed; endEpoch = epoch
            for (metric in metrics) {
                val cell = value(metric)
                if (cell.isEmpty()) continue
                val number = requireNotNull(cell.toDoubleOrNull()?.takeIf { it.isFinite() }) { "第 ${count + 2} 条记录 $metric 不是有限数值" }
                val old = ranges[metric]
                ranges[metric] = if (old == null) RecordedRange(1, number, number)
                    else RecordedRange(old.count + 1, minOf(old.minimum, number), maxOf(old.maximum, number))
            }
            count++
            require(count <= 1_000_000) { "记录超过一百万帧" }
        }
        require(count > 0) { "记录只有表头，没有飞行数据" }
        return FlightRecordSummary(aircraft.orEmpty(), count, previousElapsed - firstElapsed, startEpoch, endEpoch, ranges)
    }

    internal data class IndexedRow(val cells: List<String>, val start: Int)
    internal fun rows(text: String, checkActive: () -> Unit = {}): Sequence<List<String>> = indexedRows(text, checkActive).map { it.cells }
    internal fun indexedRows(text: String, checkActive: () -> Unit = {}): Sequence<IndexedRow> = sequence {
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closedQuote = false
        var started = false
        var index = 0
        var rowStart = 0
        var nextCheck = 0
        while (index < text.length) {
            if (index >= nextCheck) { checkActive(); nextCheck = index + 8192 }
            val c = text[index++]
            if (quoted) {
                if (c == '"') {
                    if (index < text.length && text[index] == '"') { cell.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else cell.append(c)
            } else when (c) {
                '"' -> { require(!started && !closedQuote) { "CSV 引号位置无效" }; quoted = true; started = true }
                ',', '\r', '\n' -> {
                    row += cell.toString()
                    require(row.size <= 256) { "CSV 列数过多" }
                    cell.clear(); started = false; closedQuote = false
                    if (c != ',') {
                        if (c == '\r' && index < text.length && text[index] == '\n') index++
                        if (row.size != 1 || row[0].isNotEmpty()) yield(IndexedRow(row.toList(), rowStart))
                        row.clear()
                        rowStart = index
                    }
                }
                else -> { require(!closedQuote) { "CSV 引号结束后有多余字符" }; cell.append(c); started = true }
            }
            require(cell.length <= 65536) { "CSV 字段过长" }
        }
        require(!quoted) { "CSV 引号未闭合，文件可能未写完" }
        if (started || closedQuote || row.isNotEmpty()) {
            row += cell.toString()
            require(row.size <= 256) { "CSV 列数过多" }
            yield(IndexedRow(row.toList(), rowStart))
        }
    }
}
