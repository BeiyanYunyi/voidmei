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
            assertEquals(sample.getValue("bytes").jsonPrimitive.int, bytes.size, name)
            val document = FlightModelDocument.parse(bytes.toString(Charsets.UTF_8))
            val parameters = FlightModelExtractor.extract(document)
            if (name == "bf-109g-6") {
                assertEquals(WepFuelModel(63.0, mapOf(1 to 0.05)), parameters.wepFuel)
            } else assertNull(parameters.wepFuel, name)
            val peaks = EnginePeakExtractor.extract(document)
            assertEquals(parameters.engineBindings.map { it.telemetryIndex }, peaks.map { it.telemetryIndex }, name)
            assertTrue(peaks.isNotEmpty() && peaks.all { it.peak.isFinite() && it.peak > 0 }, name)
            if (name in setOf("b-25j-1", "f-14a-early")) {
                assertEquals(listOf(1, 2), parameters.engineBindings.map { it.telemetryIndex }, name)
                assertEquals(listOf("Engine0", "Engine1"), parameters.engineBindings.map { it.instance }, name)
                val sources = if (name == "b-25j-1") listOf("EngineType0", "EngineType1") else listOf("EngineType0", "EngineType0")
                assertEquals(sources, parameters.engineBindings.map { it.parameterSource }, name)
                assertEquals(2, peaks.size, name)
                assertEquals(peaks[0].kind, peaks[1].kind, name)
                assertEquals(peaks[0].peak, peaks[1].peak, 1e-8, name)
            }
            val jets = JetThrustExtractor.extract(document)
            val pistons = PistonParameterExtractor.extract(EngineInstanceResolver.resolve(document).document())
            assertTrue(assertNotNull(parameters.emptyMassKg, name) > 0, name)
            val expectedBasicMass = mapOf("p-51d-20-na" to 3736.6, "bf-109g-6" to 2828.0, "f-86a-5" to 4911.9)
            expectedBasicMass[name]?.let { expected ->
                assertEquals(expected, assertNotNull(parameters.basicMassKg, name), 1e-8, name)
            }
            assertTrue(parameters.wings.isNotEmpty(), name)
            if (name == "f-14a-early") {
                // Real sample uses [1801, 1800]; directional semantics are not yet supported.
                assertNull(parameters.controlSpeeds.elevatorKmh)
                assertTrue(parameters.issues.any { it.contains("ElevatorsEffectiveSpeed") && it.contains("Unsupported directional thresholds") })
            } else {
                assertTrue(assertNotNull(parameters.controlSpeeds.elevatorKmh, name) > 0, name)
                assertTrue(parameters.issues.none { it.contains("ElevatorsEffectiveSpeed") }, name)
            }
            if (name == "f-14a-early") {
                val resolved = JetThrustExtractor.extract(EngineInstanceResolver.resolve(document).document())
                assertTrue(resolved.issues.isEmpty(), resolved.issues.toString())
                assertEquals(listOf("Engine0", "Engine1"), resolved.engines.map { it.source })
                for (jet in resolved.engines) {
                    // Sample base thrust 5400, boost 1.25 and final Mode6 multiplier 1.32.
                    for ((altitude, speed, military) in listOf(
                        Triple(0.0, 0.0, 4860.0), Triple(0.0, 400.0, 4914.0),
                        Triple(3049.0, 0.0, 3726.0), Triple(1524.5, 100.0, 4306.5),
                    )) assertEquals(military, assertNotNull(jet.thrust(altitude, speed)), 1e-8)
                    assertEquals(8019.0, assertNotNull(jet.thrust(0.0, 0.0, true)), 1e-8)
                    assertEquals(8513.505, assertNotNull(jet.thrust(0.0, 400.0, true)), 1e-8)
                    for (afterburner in listOf(false, true)) {
                        val values = jet.altitudesM.flatMap { altitude -> jet.velocitiesKmh.map { jet.thrust(altitude, it, afterburner) } }
                        assertTrue(values.all { it != null && it.isFinite() && it >= 0 })
                    }
                }
            } else if (name == "f-86a-5") {
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
                if (name == "b-25j-1") assertEquals(listOf("Engine0", "Engine1"), pistons.engines.map { it.source })
                if (name == "p-51d-20-na") assertTrue(parameters.engineCompressors.isNotEmpty(), name)
                pistons.engines.forEach { raw ->
                    val model = PistonModelBuilder.build(raw)
                    val wep = assertNotNull(model.wepStages, "$name: ${model.wepIssue}")
                    if (name == "b-25j-1") {
                        assertEquals(listOf(1676.0, 4115.0), raw.stages.map { it.altitudeM })
                        assertEquals(listOf(1700.0, 1450.0), raw.stages.map { it.powerHp })
                        assertTrue(wep[0].wepEnabled)
                        assertFalse(wep[1].wepEnabled)
                        assertEquals(1700.0, assertNotNull(PistonPowerModel.powerAtAltitude(model.military.stages[0], 1676.0)), 1e-8)
                    }
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
            val expectedIssues = if (name == "f-14a-early") setOf(
                "Unsupported directional thresholds: ElevatorsEffectiveSpeed (1801.0, 1800.0)",
            ) else emptySet()
            assertEquals(expectedIssues, (parameters.issues + jets.issues + pistons.issues).toSet(), name)
            if (name == "f-14a-early") {
                assertNull(parameters.structuralLoad)
                val loads = assertNotNull(parameters.sweptStructuralLoad)
                assertEquals(listOf(0.0, 0.5, 1.0), loads.profiles.map { it.sweep })
                assertEquals(listOf(1300000.0, 1400000.0, 1400000.0), loads.profiles.map { it.model.positiveForce })
                for ((sweep, force) in listOf(0.0 to 1300000.0, 0.25 to 1350000.0, 0.5 to 1400000.0, 1.0 to 1400000.0)) {
                    val limits = assertNotNull(parameters.loadLimits(1000.0, sweep))
                    assertEquals(1.2 * (2 * force / (9.80 * 19365.0) - 1), limits.maximumG, 1e-10)
                    assertEquals(1.2 * (2 * -600000.0 / (9.80 * 19365.0) + 1), limits.minimumG, 1e-10)
                }
                assertNull(parameters.loadLimits(1000.0, null))
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
                put("resolvedJetEngines", JetThrustExtractor.extract(EngineInstanceResolver.resolve(document).document()).engines.size)
                put("issues", JsonArray((parameters.issues + jets.issues).map(::JsonPrimitive)))
            }
        }
        assertEquals(setOf("p-51d-20-na", "bf-109g-6", "f-86a-5", "b-25j-1", "f-14a-early"), reports.map { it.getValue("name").jsonPrimitive.content }.toSet())
        val report = buildJsonObject {
            put("commit", manifest.getValue("commit"))
            put("samples", JsonArray(reports))
            put("limitation", "Public extracted FM samples; no live-game or physical-accuracy validation")
        }
        Files.writeString(root.resolve("validation-report.json"), Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        println(report)
    }
}
