package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import voidmei.telemetry.Engine
import voidmei.telemetry.HudEngineField

@Composable
internal fun HudEnginePanel(engines: List<Engine>, index: Int, compact: Boolean = true, fields: List<HudEngineField> = HudEngineField.entries) {
    Column {
        Text("发动机 #$index")
        val engine = engines.singleOrNull { it.index == index }
        if (engine == null) Text("此编号无可用发动机数据")
        else {
            fun Double?.shown(unit: String, digits: Int = 0): String =
                readingNumber(this, digits) + " $unit"
            val readings = fields.map { field ->
                val digits = if (!compact && field == HudEngineField.THROTTLE) 1 else field.decimals
                Triple(field.label, field.value(engine).shown(field.unit, digits), field.unit)
            }
            if (fields.isEmpty()) Text("未选择发动机读数")
            val rows = readings.map { it.first to it.second }
            FlightReadings(rows, compact = compact, unitRanges = readings.mapIndexedNotNull { index, reading ->
                val unit = reading.third
                if (unit.isEmpty()) null else index to (rows[index].second.length - unit.length until rows[index].second.length)
            }.toMap())
        }
    }
}
