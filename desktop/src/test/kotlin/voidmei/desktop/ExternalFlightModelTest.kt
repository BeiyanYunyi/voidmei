package voidmei.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*
import voidmei.fm.*
import kotlin.test.*

/** External data stays outside the repository; the manifest records exact provenance and bytes. */
class ExternalFlightModelTest {
    @Test fun publicSamplesParseAndProduceFiniteModelCurves() {
        val root = Path.of(requireNotNull(System.getenv("VOIDMEI_FM_SAMPLES")))
        val manifest = Json.parseToJsonElement(Files.readString(root.resolve("manifest.json"))).jsonObject
        val reports = mutableListOf<JsonObject>()
        for (item in manifest.getValue("files").jsonArray) {
            val sample = item.jsonObject
            val name = sample.getValue("name").jsonPrimitive.content
            require(Regex("[a-z0-9-]+").matches(name))
            val bytes = Files.readAllBytes(root.resolve("$name.blkx"))
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(sample.getValue("sha256").jsonPrimitive.content, hash, name)
            val document = FlightModelDocument.parse(bytes.toString(Charsets.UTF_8))
            val parameters = FlightModelExtractor.extract(document)
            if (name == "bf-109g-6") {
                assertEquals(WepFuelModel(63.0, mapOf(1 to 0.05)), parameters.wepFuel)
            } else assertNull(parameters.wepFuel, name)
            val peaks = EnginePeakExtractor.extract(document)
            assertEquals(parameters.engineBindings.map { it.telemetryIndex }, peaks.map { it.telemetryIndex }, name)
            assertTrue(peaks.isNotEmpty() && peaks.all { it.peak.isFinite() && it.peak > 0 }, name)
            val jets = JetThrustExtractor.extract(document)
            val pistons = PistonParameterExtractor.extract(EngineInstanceResolver.resolve(document).document())
            assertTrue(assertNotNull(parameters.emptyMassKg, name) > 0, name)
            val expectedBasicMass = mapOf("p-51d-20-na" to 3736.6, "bf-109g-6" to 2828.0, "f-86a-5" to 4911.9)
            expectedBasicMass[name]?.let { expected ->
                assertEquals(expected, assertNotNull(parameters.basicMassKg, name), 1e-8, name)
            }
            assertTrue(parameters.wings.isNotEmpty(), name)
            assertTrue(assertNotNull(parameters.controlSpeeds.elevatorKmh, name) > 0, name)
            assertTrue(parameters.issues.none { it.contains("ElevatorsEffectiveSpeed") }, name)
            if (name == "f-86a-5") {
                assertTrue(jets.engines.isNotEmpty(), "$name: ${jets.issues}")
                val resolved = JetThrustExtractor.extract(EngineInstanceResolver.resolve(document).document())
                assertTrue(resolved.issues.isEmpty(), resolved.issues.toString())
                val jet = resolved.engines.single()
                assertEquals("Engine0", jet.source)
                assertEquals(jets.engines.single().militaryKgf, jet.militaryKgf)
                assertEquals(jets.engines.single().afterburnerKgf, jet.afterburnerKgf)
                // Fixed sample facts, independently calculated from ThrustMax0=2250
                // and the four coefficients 1.01, .93, .76, .73.
                for ((altitude, speed, expected) in listOf(
                    Triple(0.0, 0.0, 2272.5), Triple(0.0, 200.0, 2092.5),
                    Triple(3000.0, 0.0, 1710.0), Triple(3000.0, 200.0, 1642.5),
                    Triple(1500.0, 100.0, 1929.375),
                )) {
                    assertEquals(expected, assertNotNull(jet.thrust(altitude, speed)), 1e-8)
                    // This sample has boost, final mode and afterburner coefficients of 1.
                    assertEquals(expected, assertNotNull(jet.thrust(altitude, speed, true)), 1e-8)
                }
                assertNull(jet.thrust(-1.0, 0.0))
                assertNull(jet.thrust(0.0, jet.velocitiesKmh.last() + 1.0))
                assertNull(jet.thrust(jet.altitudesM.last() + 1.0, 0.0))
                jets.engines.forEach { jet ->
                    val values = jet.altitudesM.flatMap { altitude -> jet.velocitiesKmh.map { jet.thrust(altitude, it) } }
                    assertTrue(values.any { it != null && it > 0 }, name)
                    assertTrue(values.all { it == null || it.isFinite() && it >= 0 }, name)
                }
            } else {
                assertTrue(pistons.engines.isNotEmpty(), "$name: ${pistons.issues}")
                if (name == "p-51d-20-na") assertTrue(parameters.engineCompressors.isNotEmpty(), name)
                pistons.engines.forEach { raw ->
                    val model = PistonModelBuilder.build(raw)
                    val wep = assertNotNull(model.wepStages, "$name: ${model.wepIssue}")
                    for ((stages, isWep) in listOf(model.military.stages to false, wep to true)) {
                        for (speed in listOf(0.0, 450.0)) {
                            val curve = PistonPowerModel.curve(stages, speedKmh = speed, stepM = 25, wep = isWep)
                            assertEquals(401, curve.size, name)
                            assertTrue(curve.any { it != null && it.powerHp > 0 }, name)
                            assertTrue(curve.all { it == null || it.powerHp.isFinite() && it.powerHp >= 0 }, name)
                            assertNotNull(PowerCurveSummary.from(curve).peak, name)
                        }
                    }
                }
            }
            reports += buildJsonObject {
                put("name", name); put("sha256", hash)
                put("emptyMassKg", parameters.emptyMassKg)
                put("basicMassKg", parameters.basicMassKg)
                put("enginePeaks", JsonArray(peaks.map { peak -> buildJsonObject {
                    put("index", peak.telemetryIndex); put("kind", peak.kind.name); put("peak", peak.peak)
                } }))
                put("wingConfigurations", parameters.wings.size)
                put("elevatorEffectiveSpeedKmh", parameters.controlSpeeds.elevatorKmh)
                put("pistonEngines", pistons.engines.size)
                put("jetModels", jets.engines.size)
                put("issues", JsonArray((parameters.issues + jets.issues).map(::JsonPrimitive)))
            }
        }
        assertEquals(setOf("p-51d-20-na", "bf-109g-6", "f-86a-5"), reports.map { it.getValue("name").jsonPrimitive.content }.toSet())
        val report = buildJsonObject {
            put("commit", manifest.getValue("commit"))
            put("samples", JsonArray(reports))
            put("limitation", "Public extracted FM samples; no live-game or physical-accuracy validation")
        }
        Files.writeString(root.resolve("validation-report.json"), Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        println(report)
    }
}
