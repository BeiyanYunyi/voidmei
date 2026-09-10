package voidmei.recording

data class EngineTimelineRecord(val text: String, val analysis: FlightRecordAnalysis)

/** Join monotonically ordered sample IDs; timestamps must agree but do not define the time axis. */
object EngineRecordTimeline {
    fun analyze(flightText: String, engineText: String, engineIndex: Int): FlightRecordAnalysis =
        read(flightText, engineText, engineIndex).analysis

    /** Retain the validated full-resolution join for subsequent window analysis. */
    fun read(flightText: String, engineText: String, engineIndex: Int): EngineTimelineRecord {
        FlightRecordReader.summarize(flightText)
        require(EngineRecordReader.summarize(engineText).any { it.index == engineIndex }) { "记录不包含所选发动机" }
        val flightRows = FlightRecordReader.rows(flightText.removePrefix("\uFEFF")).iterator()
        val engineRows = FlightRecordReader.rows(engineText.removePrefix("\uFEFF")).iterator()
        val flightColumns = flightRows.next().withIndex().associate { it.value to it.index }
        val engineColumns = engineRows.next().withIndex().associate { it.value to it.index }
        val metrics = FlightCsv.engineHeader.split(',').drop(3).filter { it in engineColumns }
        var nextEngine = if (engineRows.hasNext()) engineRows.next() else null
        val csv = StringBuilder("sample_id,utc_epoch_ms,elapsed_ms,aircraft," + metrics.joinToString(",") + "\n")
        fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
        while (flightRows.hasNext()) {
            val flight = flightRows.next()
            val id = flight[flightColumns.getValue("sample_id")].toLong()
            val epoch = flight[flightColumns.getValue("utc_epoch_ms")].toLongOrNull()
            var selected: List<String>? = null
            while (true) {
                val engine = nextEngine ?: break
                val sample = engine[engineColumns.getValue("sample_id")].toLong()
                if (sample > id) break
                require(sample == id) { "发动机采样 $sample 不在飞行记录中" }
                require(epoch != null && epoch == engine[engineColumns.getValue("utc_epoch_ms")].toLong()) { "采样 $id 的两个文件时间不匹配" }
                if (engine[engineColumns.getValue("engine_index")].toInt() == engineIndex) selected = engine
                nextEngine = if (engineRows.hasNext()) engineRows.next() else null
            }
            val values = listOf("sample_id", "utc_epoch_ms", "elapsed_ms", "aircraft").map { flight[flightColumns.getValue(it)] } +
                metrics.map { key -> selected?.get(engineColumns.getValue(key)).orEmpty() }
            csv.append(values.joinToString(",", transform = ::quote)).append('\n')
            require(csv.length <= FlightRecordReader.MAX_BYTES) { "关联结果超过 64 MiB 限制" }
        }
        require(nextEngine == null) { "发动机记录包含飞行记录之外的采样" }
        val text = csv.toString()
        val analysis = FlightRecordPlots.analyze(text, fields = metrics).copy(
            notes = listOf("发动机 #$engineIndex · 按采样编号和 UTC 校验关联，横轴使用飞行记录的经过时间；缺少发动机采样处断线。"))
        return EngineTimelineRecord(text, analysis)
    }
}
