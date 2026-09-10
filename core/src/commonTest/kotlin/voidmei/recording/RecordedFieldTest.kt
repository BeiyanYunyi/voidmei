package voidmei.recording

import kotlin.test.*

class RecordedFieldTest {
    @Test fun everyRecordedMetricCanBePlottedWithoutDroppingSchemaColumns() {
        val metrics = FlightCsv.flightHeader.split(',').drop(4)
        assertEquals(metrics.toSet(), RecordedField.entries.map { it.id }.toSet())
        assertEquals(metrics.size, RecordedField.entries.size)
        fun scale(column: Int) = (column + 1) * if (metrics[column] in setOf("heading_deg", "roll_deg")) 0.01 else 1.0
        val rows = (0..1000).joinToString("\n") { sample ->
            "$sample,${1000 + sample},$sample,test," + metrics.indices.joinToString(",") { column ->
                (sample * scale(column)).toString()
            }
        }
        val result = FlightRecordPlots.analyze(FlightCsv.flightHeader + "\n" + rows, buckets = 10)
        metrics.forEachIndexed { column, key ->
            assertEquals(RecordedRange(1001, 0.0, 1000.0 * scale(column)), result.summary.ranges[key])
            val points = result.plots.getValue(key)
            assertTrue(points.size <= 40)
            assertEquals(0.0, points.first().value)
            assertEquals(1000.0 * scale(column), points.last().value)
            assertTrue(points.drop(1).all { it.connectFromPrevious })
        }
    }

    @Test fun extraControlFieldsPreserveMissingSamplesAndColumnReordering() {
        val result = FlightRecordPlots.analyze(
            "sample_id,aircraft,elevator_percent,elapsed_ms,utc_epoch_ms,total_power_hp\n" +
                "0,test,-50,0,1000,1200\n1,test,,100,1100,\n2,test,50,200,1200,1300\n")
        for (key in listOf("elevator_percent", "total_power_hp")) {
            assertEquals(2, result.summary.ranges[key]?.count)
            assertTrue(result.plots.getValue(key).none { it.connectFromPrevious })
        }
        assertNull(result.plots["ias_kmh"])
    }
}
