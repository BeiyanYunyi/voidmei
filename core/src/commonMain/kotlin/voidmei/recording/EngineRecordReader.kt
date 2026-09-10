package voidmei.recording

/** UTC is recorded metadata, not a monotonic clock or a source of flight duration. */
data class EngineRecordSummary(val index: Int, val samples: Int, val firstSampleId: Long, val lastSampleId: Long,
    val firstEpochMs: Long, val lastEpochMs: Long, val ranges: Map<String, RecordedRange>)

object EngineRecordReader {
    fun summarize(text: String): List<EngineRecordSummary> {
        require(text.length <= FlightRecordReader.MAX_BYTES) { "记录超过 64 MiB 限制" }
        val rows = FlightRecordReader.rows(text.removePrefix("\uFEFF")).iterator()
        require(rows.hasNext()) { "记录为空" }
        val header = rows.next()
        require(header.distinct().size == header.size && header.none { it.isEmpty() }) { "CSV 表头包含重复或空列名" }
        val columns = header.withIndex().associate { it.value to it.index }
        require(listOf("sample_id", "utc_epoch_ms", "engine_index").all { it in columns }) { "不是 Kotlin 发动机 CSV（缺少必要列）" }
        val fields = FlightCsv.engineHeader.split(',').drop(3).filter { it in columns }
        require(fields.isNotEmpty()) { "记录中没有可识别的发动机数据列" }
        val engines = mutableMapOf<Int, EngineRecordSummary>()
        var previousSample = -1L
        var previousEpoch = -1L
        var rowCount = 0
        while (rows.hasNext()) {
            val row = rows.next()
            require(++rowCount <= 1_000_000) { "记录超过一百万条" }
            require(row.size == header.size) { "第 ${rowCount + 1} 条记录列数不符" }
            fun integer(key: String) = requireNotNull(row[columns.getValue(key)].toLongOrNull()?.takeIf { it >= 0 }) { "$key 无效" }
            val sample = integer("sample_id")
            val epoch = integer("utc_epoch_ms")
            val rawIndex = integer("engine_index")
            require(rawIndex in 1..Int.MAX_VALUE.toLong()) { "发动机编号无效" }
            val index = rawIndex.toInt()
            require(sample >= previousSample) { "采样编号倒退" }
            require(sample != previousSample || epoch == previousEpoch) { "同一采样的 UTC 时间不一致" }
            val old = engines[index]
            require(old == null || sample > old.lastSampleId) { "同一采样包含重复发动机" }
            val ranges = old?.ranges?.toMutableMap() ?: mutableMapOf()
            for (field in fields) {
                val cell = row[columns.getValue(field)]
                if (cell.isEmpty()) continue
                val value = requireNotNull(cell.toDoubleOrNull()?.takeIf { it.isFinite() }) { "$field 不是有限数值" }
                val prior = ranges[field]
                ranges[field] = if (prior == null) RecordedRange(1, value, value)
                    else RecordedRange(prior.count + 1, minOf(prior.minimum, value), maxOf(prior.maximum, value))
            }
            engines[index] = EngineRecordSummary(index, (old?.samples ?: 0) + 1, old?.firstSampleId ?: sample,
                sample, old?.firstEpochMs ?: epoch, epoch, ranges)
            require(engines.size <= 128) { "发动机种类超过 128 个" }
            previousSample = sample
            previousEpoch = epoch
        }
        require(engines.isNotEmpty()) { "记录只有表头，没有发动机数据" }
        return engines.values.sortedBy { it.index }
    }
}
