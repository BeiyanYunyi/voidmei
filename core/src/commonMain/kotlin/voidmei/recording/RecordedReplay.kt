package voidmei.recording

data class RecordedFrame(val sampleId: Long, val elapsedMs: Long, val epochMs: Long?, val aircraft: String,
    val values: Map<String, Double?>)

/** Index row offsets and monotonic times; keep values in the source instead of materializing every frame. */
class RecordedReplay(source: String, fields: List<String> = FlightRecordPlots.fields, checkActive: () -> Unit = {}) {
    private val text = source.removePrefix("\uFEFF")
    private val summary = FlightRecordReader.summarize(text, fields, checkActive)
    private val offsets = IntArray(summary.samples)
    private val times = LongArray(summary.samples)
    private val columns: Map<String, Int>
    private val metrics: List<String>
    val size get() = offsets.size
    val spanMs get() = summary.spanMs

    init {
        val rows = FlightRecordReader.indexedRows(text, checkActive).iterator()
        columns = rows.next().cells.withIndex().associate { it.value to it.index }
        metrics = fields.distinct().filter { it in columns }
        var origin = 0L
        var index = 0
        while (rows.hasNext()) {
            checkActive()
            val row = rows.next()
            val time = row.cells[columns.getValue("elapsed_ms")].toLong()
            if (index == 0) origin = time
            offsets[index] = row.start
            times[index] = time - origin
            index++
        }
    }

    fun timeAt(index: Int): Long = times[index]

    /** Latest frame at or before the requested time, including the last of simultaneous samples. */
    fun indexAt(timeMs: Long): Int {
        require(timeMs >= 0)
        var lower = 0
        var upper = size
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (times[middle] <= timeMs) lower = middle + 1 else upper = middle
        }
        return (lower - 1).coerceAtLeast(0)
    }

    fun indicesWithin(startMs: Long, endMs: Long): IntRange? {
        require(startMs >= 0 && endMs >= startMs)
        var lower = 0
        var upper = size
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (times[middle] < startMs) lower = middle + 1 else upper = middle
        }
        val last = indexAt(endMs)
        return if (lower <= last && lower < size && times[lower] <= endMs) lower..last else null
    }

    /** IDs are strictly increasing, even when elapsed timestamps repeat. */
    fun indexOfSample(sampleId: Long): Int? {
        var lower = 0
        var upper = size
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            val id = frame(middle).sampleId
            if (id == sampleId) return middle
            if (id < sampleId) lower = middle + 1 else upper = middle
        }
        return null
    }

    fun frame(index: Int): RecordedFrame {
        require(index in offsets.indices)
        val end = if (index == offsets.lastIndex) text.length else offsets[index + 1]
        val row = FlightRecordReader.rows(text.substring(offsets[index], end)).first()
        fun value(key: String) = row[columns.getValue(key)]
        return RecordedFrame(value("sample_id").toLong(), times[index], value("utc_epoch_ms").toLongOrNull(),
            value("aircraft"), metrics.associateWith { value(it).toDoubleOrNull() })
    }
}
