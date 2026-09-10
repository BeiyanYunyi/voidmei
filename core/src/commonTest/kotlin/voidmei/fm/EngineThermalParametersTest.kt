package voidmei.fm

import kotlin.test.*

class EngineThermalParametersTest {
    private val source = """
        EngineType0 { Main { Type:t=Inline } Temperature {
            Load0 { WaterTemperature:r=80; OilTemperature:r=60 }
            Load1 { WaterTemperature:r=90; OilTemperature:r=70; WorkTime:r=7200; RecoverTime:r=3600 }
            Load2 { WaterTemperature:r=110; OilTemperature:r=80; WorkTime:r=1800; RecoverTime:r=900 }
        } }
        Engine0 { Type:i=0; Temperature { Load2 { WorkTime:r=1200 } } }
        Engine1 { Type:i=0 }
    """
    private fun extract(text: String = source) = FlightModelExtractor.extract(BlkParser.parse(text))

    @Test fun retainsUnknownBaselineTimesAndPerInstanceOverrides() {
        val models = extract().engineThermals
        assertEquals(listOf(1, 2), models.map { it.telemetryIndex })
        assertEquals(listOf(1200.0, 1800.0), models.map { it.bands.last().workSeconds })
        assertEquals(listOf(900.0, 900.0), models.map { it.bands.last().recoverSeconds })
        assertNull(models.first().bands.first().workSeconds)
        assertNull(models.first().bands.first().recoverSeconds)
        val zero = extract(source.replace("WaterTemperature:r=80;", "WorkTime:r=0; RecoverTime:r=0; WaterTemperature:r=80;"))
        assertEquals(0.0, zero.engineThermals.first().bands.first().workSeconds)
    }

    @Test fun invalidOrAmbiguousThermalDataIsNotPartiallyAccepted() {
        for (bad in listOf(source.replace("WorkTime:r=7200", "WorkTime:r=-1"),
            source.replace("WaterTemperature:r=110", "WaterTemperature:r=85"),
            source.replace("Load2 { WaterTemperature", "Load01 { WaterTemperature"),
            source.replace("WorkTime:r=7200", "WorkTime:t=unknown"))) {
            val result = extract(bad)
            assertTrue(result.engineThermals.isEmpty(), bad)
            assertTrue(result.issues.isNotEmpty(), bad)
        }
    }

    @Test fun singleTemperatureChannelRemainsUsableWithoutInventingTheOther() {
        val model = extract("Engine0 { Main { Type:t=Jet } Temperature { Load0 { OilTemperature:r=85 } Load1 { OilTemperature:r=90; WorkTime:r=7200; RecoverTime:r=120 } } }")
        assertEquals(1, model.engineThermals.size)
        assertNull(model.engineThermals.single().bands.last().waterTemperatureC)
        assertEquals(90.0, model.engineThermals.single().bands.last().oilTemperatureC)
    }
}
