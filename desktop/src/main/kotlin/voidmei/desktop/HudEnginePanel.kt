package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import voidmei.telemetry.*

/** A global engine alert must also match the engine represented by this reading. */
internal fun engineReadingWarnings(flight: ConnectionState.Flying, index: Int, model: AircraftAlertModel?,
    alerts: List<FlightAlert>, thermal: EngineThermalObservation? = null): Map<HudEngineField, String> {
    val telemetry = flight.telemetry
    val parameters = model?.parametersFor(telemetry.aircraft)
    return buildMap {
        if (FlightAlert.ENGINE_OVERHEAT in alerts) thermal?.warningChannels(flight, model)?.filter { it.telemetryIndex == index }?.forEach {
            put(if (it.channel == EngineTemperatureChannel.WATER) HudEngineField.WATER_TEMPERATURE else HudEngineField.OIL_TEMPERATURE,
                FlightAlert.ENGINE_OVERHEAT.label)
        }
        if (FlightAlert.LOW_RPM in alerts && index in EngineWarnings.lowRpm(telemetry, parameters?.engineRpmReferences.orEmpty()))
            put(HudEngineField.RPM, FlightAlert.LOW_RPM.label)
        if (FlightAlert.HIGH_RPM in alerts && index in EngineWarnings.highRpm(telemetry, parameters?.engineRpmLimits.orEmpty()))
            put(HudEngineField.RPM, FlightAlert.HIGH_RPM.label)
        if (FlightAlert.NEGATIVE_LOAD_LOW_THRUST in alerts && index in EngineWarnings.lowThrustUnderNegativeLoad(telemetry))
            put(HudEngineField.THRUST, FlightAlert.NEGATIVE_LOAD_LOW_THRUST.label)
        if (FlightAlert.COMPRESSOR_STAGE in alerts && CompressorAdvice.recommendations(telemetry,
                parameters?.engineCompressors.orEmpty()).any { it.engineIndex == index })
            put(HudEngineField.COMPRESSOR, FlightAlert.COMPRESSOR_STAGE.label)
        if (FlightAlert.ENGINE_OVERHEAT in alerts && thermal?.hudBudget(flight, model, index) != null &&
            index in thermal.warningEngines(flight, model))
            put(HudEngineField.HEAT_BUDGET, FlightAlert.ENGINE_OVERHEAT.label)
    }
}

@Composable
internal fun HudEnginePanel(engines: List<Engine>, index: Int, compact: Boolean = true, fields: List<HudEngineField> = HudEngineField.selected(HudEngineField.defaults),
    warnings: Map<HudEngineField, String> = emptyMap(), showInstruments: Boolean = true,
    heatBudget: ThermalBudgetRange? = null, powerPercent: PowerPercentReading? = null, tasKmh: Double? = null) {
    Column {
        Text("发动机 #$index")
        val engine = engines.singleOrNull { it.index == index }
        if (engine == null) Text("此编号无可用发动机数据")
        else {
            fun Double?.shown(unit: String, digits: Int = 0): String =
                readingNumber(this, digits) + " $unit"
            val readings = fields.map { field ->
                val digits = if (!compact && field == HudEngineField.THROTTLE) 1 else field.decimals
                if (field == HudEngineField.FM_POWER_PERCENT) {
                    val unit = powerPercent?.let { "% · ${it.source}" } ?: "%"
                    return@map Triple(field.label, powerPercent?.percent.shown(unit, digits), unit)
                }
                Triple(field.label, if (field == HudEngineField.HEAT_BUDGET) formatThermalBudget(heatBudget)
                    else field.value(engine, tasKmh).shown(field.unit, digits), field.unit)
            }
            if (fields.isEmpty()) Text("未选择发动机读数")
            val rows = readings.map { it.first to it.second }
            val warningRows = fields.mapIndexedNotNull { row, field ->
                warnings[field]?.takeIf { if (field == HudEngineField.HEAT_BUDGET) heatBudget?.roundForDisplay() != null
                    else field.value(engine) != null }?.let { row to it }
            }.toMap()
            FlightReadings(rows, compact = compact, warningRows = warningRows, unitRanges = readings.mapIndexedNotNull { index, reading ->
                val unit = reading.third
                if (unit.isEmpty()) null else index to (rows[index].second.length - unit.length until rows[index].second.length)
            }.toMap())
            if (compact && showInstruments && HudEngineField.THROTTLE in fields)
                ThrottleBar(HudEngineField.THROTTLE.value(engine), index, "hud-engine-throttle-$index")
            if (compact && showInstruments) EngineControlBars(engine, fields, powerPercent)
        }
    }
}
