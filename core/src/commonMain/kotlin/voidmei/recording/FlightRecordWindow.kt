package voidmei.recording

data class RecordWindowAnalysis(val analysis: FlightRecordAnalysis, val firstPointOffsetMs: Long, val text: String)

object FlightRecordWindow {
    /** Inclusive boundaries, measured from the first record; resamples original rows, not prior plot points. */
    fun analyze(text: String, startMs: Long, endMs: Long,
        fields: List<String> = FlightRecordPlots.fields, checkActive: () -> Unit = {}): RecordWindowAnalysis {
        val full = FlightRecordReader.summarize(text, fields, checkActive)
        require(startMs >= 0 && endMs >= startMs && endMs <= full.spanMs) { "时间区间需在 0–${full.spanMs / 1000.0} 秒内" }
        val rows = FlightRecordReader.rows(text.removePrefix("\uFEFF"), checkActive).iterator()
        val header = rows.next()
        val timeColumn = header.indexOf("elapsed_ms")
        fun encode(row: List<String>) = row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
        val cropped = StringBuilder(encode(header)).append('\n')
        var origin: Long? = null
        var first: Long? = null
        while (rows.hasNext()) {
            checkActive()
            val row = rows.next()
            val elapsed = row[timeColumn].toLong()
            if (origin == null) origin = elapsed
            val relative = elapsed - origin
            if (relative in startMs..endMs) {
                if (first == null) first = relative
                cropped.append(encode(row)).append('\n')
                require(cropped.length <= FlightRecordReader.MAX_BYTES) { "区间记录超过 64 MiB 限制" }
            }
        }
        require(first != null) { "所选区间没有采样记录" }
        val selectedText = cropped.toString()
        return RecordWindowAnalysis(FlightRecordPlots.analyze(selectedText, fields = fields, checkActive = checkActive), first, selectedText)
    }
}
