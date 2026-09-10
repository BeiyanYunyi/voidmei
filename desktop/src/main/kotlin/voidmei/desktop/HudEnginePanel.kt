package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import voidmei.telemetry.Engine
import java.util.Locale

@Composable
internal fun HudEnginePanel(engines: List<Engine>, index: Int, compact: Boolean = true) {
    Column {
        Text("发动机 #$index")
        val engine = engines.singleOrNull { it.index == index }
        if (engine == null) Text("此编号无可用发动机数据")
        else {
            fun Double?.shown(unit: String, digits: Int = 0): String =
                (this?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.${digits}f", it) } ?: "—") + " $unit"
            val readings = listOf(
                Triple("油门", engine.throttlePercent.shown("%", if (compact) 0 else 1), "%"),
                Triple("转速", engine.rpm.shown("RPM"), "RPM"),
                Triple("功率", engine.powerHp.shown("hp"), "hp"),
                Triple("推力", engine.thrustKgf.shown("kgf"), "kgf"),
                Triple("水温", engine.waterTemperatureC.shown("°C", 1), "°C"),
                Triple("油温", engine.oilTemperatureC.shown("°C", 1), "°C"),
                Triple("转速控制", engine.rpmControlPercent.shown("%"), "%"),
                Triple("混合比", engine.mixturePercent.shown("%"), "%"),
                Triple("水散热器", engine.radiatorPercent.shown("%"), "%"),
                Triple("油散热器", engine.oilRadiatorPercent.shown("%"), "%"),
                Triple("增压器档位", engine.compressorStage.shown(""), ""),
                Triple("磁电机", engine.magneto.shown(""), ""),
                Triple("进气压力", engine.manifoldPressureAtm.shown("atm", 2), "atm"),
                Triple("桨叶角", engine.propellerPitchDeg.shown("°", 1), "°"),
                Triple("效率", engine.efficiencyPercent.shown("%"), "%"),
            )
            val rows = readings.map { it.first to it.second }
            FlightReadings(rows, compact = compact, unitRanges = readings.mapIndexedNotNull { index, reading ->
                val unit = reading.third
                if (unit.isEmpty()) null else index to (rows[index].second.length - unit.length until rows[index].second.length)
            }.toMap())
        }
    }
}
