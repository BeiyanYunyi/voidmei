package voidmei.recording

data class RecordedPlotPoint(val elapsedMs: Long, val value: Double, val connectFromPrevious: Boolean, val sampleId: Long? = null)
data class FlightRecordAnalysis(val summary: FlightRecordSummary, val plots: Map<String, List<RecordedPlotPoint>>,
    val notes: List<String> = emptyList())

object FlightRecordPlots {
    val fields = RecordedField.entries.map { it.id }
    private val cyclicAngles = setOf("heading_deg", "roll_deg")
    private fun crossesAngularBoundary(field: String, previous: Double?, current: Double?) =
        field in cyclicAngles && previous != null && current != null && kotlin.math.abs(current - previous) > 180

    /** At most four points per time bucket. Gaps break lines conservatively within a bucket. */
    fun analyze(text: String, buckets: Int = 250, fields: List<String> = this.fields, checkActive: () -> Unit = {}): FlightRecordAnalysis {
        require(buckets in 1..1000)
        require(fields.isNotEmpty() && fields.size <= 64 && fields.distinct().size == fields.size)
        val summary = FlightRecordReader.summarize(text, fields, checkActive) // Validate before trusting rows in the second pass.
        val rows = FlightRecordReader.rows(text.removePrefix("\uFEFF"), checkActive).iterator()
        val header = rows.next()
        val timeIndex = header.indexOf("elapsed_ms")
        val sampleIndex = header.indexOf("sample_id")
        val indices = fields.associateWith { header.indexOf(it) }.filterValues { it >= 0 }
        val groups = indices.mapValues { Array(buckets) { Bucket() } }
        val previousValues = mutableMapOf<String, Double?>()
        var origin: Long? = null
        var previousTime = 0L
        var ordinal = 0
        while (rows.hasNext()) {
            checkActive()
            val row = rows.next()
            val elapsed = row[timeIndex].toLong()
            if (origin == null) origin = elapsed
            val relative = elapsed - origin
            val index = if (summary.spanMs == 0L) 0 else
                (relative.toDouble() / summary.spanMs * buckets).toInt().coerceIn(0, buckets - 1)
            for ((key, column) in indices) {
                val bucket = groups.getValue(key)[index]
                val value = row[column].toDoubleOrNull()
                if (value == null || (ordinal > 0 && elapsed - previousTime > 2000)) bucket.broken = true
                if (crossesAngularBoundary(key, previousValues[key], value)) bucket.broken = true
                previousValues[key] = value
                if (value != null) bucket.add(Sample(ordinal, relative, value, row[sampleIndex].toLong()))
            }
            previousTime = elapsed
            ordinal++
        }
        val plots = groups.mapValues { (key, bins) ->
            buildList<RecordedPlotPoint> {
                var previousBroken = true
                for (bin in bins) {
                    checkActive()
                    val points = listOfNotNull(bin.first, bin.minimum, bin.maximum, bin.last).distinctBy { it.ordinal }.sortedBy { it.ordinal }
                    if (points.isEmpty()) {
                        if (bin.broken) previousBroken = true
                        continue
                    }
                    for ((i, sample) in points.withIndex()) {
                        add(RecordedPlotPoint(sample.time, sample.value, isNotEmpty() && !bin.broken &&
                            (i > 0 || !previousBroken) && !crossesAngularBoundary(key, lastOrNull()?.value, sample.value), sample.id))
                    }
                    previousBroken = bin.broken
                }
            }
        }
        return FlightRecordAnalysis(summary, plots)
    }

    private data class Sample(val ordinal: Int, val time: Long, val value: Double, val id: Long)
    private class Bucket {
        var first: Sample? = null
        var last: Sample? = null
        var minimum: Sample? = null
        var maximum: Sample? = null
        var broken = false
        fun add(sample: Sample) {
            if (first == null) first = sample
            last = sample
            if (minimum == null || sample.value < minimum!!.value) minimum = sample
            if (maximum == null || sample.value > maximum!!.value) maximum = sample
        }
    }
}
