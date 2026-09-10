package voidmei.desktop

import java.nio.file.*
import kotlin.test.*

class FlightModelRepositoryTest {
    @Test fun cancelledLoadIsNotConvertedToInvalidOrCached() = fixture { root, dir ->
        Files.writeString(dir.resolve("test.blkx"), "fmFile:t=\"fm/test.blk\"")
        val path = dir.resolve("fm/test.blkx")
        Files.writeString(path, "Mass { EmptyMass:r=2500 }")
        var totalChecks = 0
        assertIs<FlightModelState.Ready>(FlightModelRepository(root).load("test") { totalChecks++ })
        for (cancelAt in listOf(1, 4, totalChecks)) {
            val repository = FlightModelRepository(root)
            val cancelled = java.util.concurrent.CancellationException("switch aircraft")
            var checks = 0
            assertSame(cancelled, assertFailsWith<java.util.concurrent.CancellationException> {
                repository.load("test") { if (++checks == cancelAt) throw cancelled }
            })
            Files.writeString(path, "Mass { EmptyMass:r=${2500 + cancelAt} }")
            val loaded = assertIs<FlightModelState.Ready>(repository.load("test"))
            assertEquals((2500 + cancelAt).toDouble(), loaded.document.fields().single().second.number())
        }
    }

    @Test fun listsOnlySupportedCentralFilesAndCanCancelScanning() = fixture { root, dir ->
        Files.writeString(dir.resolve("zulu.blkx"), "not yet validated")
        Files.writeString(dir.resolve("alpha-1.blkx"), "")
        Files.writeString(dir.resolve("bad name.blkx"), "")
        Files.writeString(dir.resolve("notes.txt"), "")
        Files.createDirectory(dir.resolve("folder.blkx"))
        Files.writeString(dir.resolve("fm/inner.blkx"), "")
        val repository = FlightModelRepository(root)
        assertEquals(listOf("alpha-1", "zulu"), repository.listAircraft())
        val cancelled = java.util.concurrent.CancellationException("stop listing")
        var checks = 0
        assertSame(cancelled, assertFailsWith<java.util.concurrent.CancellationException> {
            repository.listAircraft { if (++checks == 2) throw cancelled }
        })
    }
    @Test fun rejectsDirectoryModelsAndReloadsAfterReplacement() = fixture { root, dir ->
        val central = dir.resolve("test.blkx")
        Files.createDirectory(central)
        val repository = FlightModelRepository(root)
        assertTrue(assertIs<FlightModelState.Invalid>(repository.load("test")).reason.contains("regular file"))
        Files.delete(central)
        Files.writeString(central, "fmFile:t=\"fm/test.blk\"")
        val model = dir.resolve("fm/test.blkx")
        Files.createDirectory(model)
        repository.clear()
        assertTrue(assertIs<FlightModelState.Invalid>(repository.load("test")).reason.contains("regular file"))
        Files.delete(model)
        Files.writeString(model, "Mass { EmptyMass:r=2500 }")
        repository.clear()
        assertIs<FlightModelState.Ready>(repository.load("test"))
    }

    @org.junit.Test(timeout = 5000) fun rejectsNamedPipesBeforeOpeningTheirStreams() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        fixture { root, dir ->
            val central = dir.resolve("test.blkx")
            fun pipe(path: Path) {
                val process = ProcessBuilder("mkfifo", path.toString()).start()
                assertTrue(process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
            }
            pipe(central)
            val repository = FlightModelRepository(root)
            assertTrue(assertIs<FlightModelState.Invalid>(repository.load("test")).reason.contains("regular file"))
            Files.delete(central)
            Files.writeString(central, "fmFile:t=\"fm/test.blk\"")
            pipe(dir.resolve("fm/test.blkx"))
            repository.clear()
            assertTrue(assertIs<FlightModelState.Invalid>(repository.load("test")).reason.contains("regular file"))
        }
    }

    private fun fixture(test: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("voidmei-fm")
        val dir = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        try { test(root, dir) } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun loadsCentralReferenceAndPreservesModelData() = fixture { root, dir ->
        Files.writeString(dir.resolve("test.blkx"), "fmFile:t=\"/fm/shared.blk\"\nmodifications { ussr_fuel_b-100 { effects { addHorsePowers:i=50 } } }")
        Files.writeString(dir.resolve("fm/shared.blkx"), "Mass { EmptyMass:r=2500 }\nEngine { Power:r=1000 }")
        val ready = assertIs<FlightModelState.Ready>(FlightModelRepository(root).load(" TEST "))
        assertEquals("test", ready.aircraft)
        assertEquals(2500.0, ready.document.fields().first().second.number())
        val fuel = voidmei.fm.FuelModificationExtractor.extract(assertNotNull(ready.central)).options.single()
        assertEquals("ussr_fuel_b-100", fuel.id)
        assertEquals(50.0, fuel.addedHorsepower)
    }

    @Test fun loadsJsonCentralAndFmWithoutChangingTypedBlkSupport() = fixture { root, dir ->
        Files.writeString(dir.resolve("test.blkx"), """{"fmFile":"fm/shared.blk"}""")
        Files.writeString(dir.resolve("fm/shared.blkx"), """{"EmptyMass":2500,"Vne":800,"FlapsDestructionIndSpeedP":[0.5,500,1,300]}""")
        val ready = assertIs<FlightModelState.Ready>(FlightModelRepository(root).load("test"))
        val parameters = voidmei.fm.FlightModelExtractor.extract(ready.document)
        assertEquals(2500.0, parameters.emptyMassKg)
        assertEquals(400.0, parameters.flapLimits!!.speedAt(75.0))
    }

    @Test fun discoversExportLayoutAndAcceptsDirectFlightmodelsSelection() = fixture { root, original ->
        val exported = root.resolve("aces.vromfs.bin_u/gamedata/flightmodels")
        Files.createDirectories(exported.parent)
        Files.move(original, exported)
        Files.writeString(exported.resolve("test.blkx"), """{"fmFile":"fm/test.blk"}""")
        Files.writeString(exported.resolve("fm/test.blkx"), """{"EmptyMass":6480}""")
        for (selection in listOf(root, root.resolve("aces.vromfs.bin_u"), exported)) {
            val ready = assertIs<FlightModelState.Ready>(FlightModelRepository(selection).load("test"))
            assertEquals(exported.toRealPath().toString(), ready.dataDirectory)
            assertEquals(6480.0, voidmei.fm.FlightModelExtractor.extract(ready.document).emptyMassKg)
        }
    }

    @Test fun ambiguousRootsRequireExplicitSelectionAndReloadRechecksLayout() = fixture { root, original ->
        Files.writeString(original.resolve("test.blkx"), "fmFile:t=\"fm/test.blk\"")
        Files.writeString(original.resolve("fm/test.blkx"), "EmptyMass:r=1000")
        val repo = FlightModelRepository(root)
        assertIs<FlightModelState.Ready>(repo.load("test"))
        val alternate = Files.createDirectories(root.resolve("aces.vromfs.bin_u/gamedata/flightmodels/fm")).parent
        Files.writeString(alternate.resolve("test.blkx"), """{"fmFile":"fm/test.blk"}""")
        Files.writeString(alternate.resolve("fm/test.blkx"), """{"EmptyMass":2000}""")
        repo.clear()
        val invalid = assertIs<FlightModelState.Invalid>(repo.load("test"))
        assertTrue(invalid.reason.contains("多个 flightmodels"))
        val selected = assertIs<FlightModelState.Ready>(FlightModelRepository(alternate).load("test"))
        assertEquals(2000.0, voidmei.fm.FlightModelExtractor.extract(selected.document).emptyMassKg)
    }

    @Test fun missingCacheRequiresExplicitReloadAndCorruptDataIsNotReady() = fixture { root, dir ->
        val repo = FlightModelRepository(root)
        assertIs<FlightModelState.Missing>(repo.load("test"))
        Files.writeString(dir.resolve("test.blkx"), "name:t=\"test\"")
        Files.writeString(dir.resolve("fm/test.blkx"), "Mass { EmptyMass:r=1 }")
        assertIs<FlightModelState.Missing>(repo.load("test"))
        repo.clear()
        assertIs<FlightModelState.Ready>(repo.load("test"))
        Files.writeString(dir.resolve("fm/test.blkx"), "Mass {")
        repo.clear()
        assertIs<FlightModelState.Invalid>(repo.load("test"))
    }

    @Test fun duplicateJsonCentralReferencesAreRejected() = fixture { root, dir ->
        Files.writeString(dir.resolve("test.blkx"), """{"fmFile":"fm/first.blk","fmFile":"fm/second.blk"}""")
        val invalid = assertIs<FlightModelState.Invalid>(FlightModelRepository(root).load("test"))
        assertTrue(invalid.reason.contains("Ambiguous fmFile"))
    }

    @Test fun traversalAndInvalidIdentifiersAreRejected() = fixture { root, dir ->
        val repo = FlightModelRepository(root)
        assertIs<FlightModelState.Invalid>(repo.load("../test"))
        assertIs<FlightModelState.Unresolved>(repo.load(null))
        Files.writeString(dir.resolve("test.blkx"), "fmFile:t=\"../../secret.blk\"")
        assertIs<FlightModelState.Invalid>(repo.load("test"))
    }

    @Test fun aircraftSwitchNeverReturnsAnotherAircraftModel() = fixture { root, dir ->
        Files.writeString(dir.resolve("first.blkx"), "fmFile:t=\"fm/first.blkx\"")
        Files.writeString(dir.resolve("fm/first.blkx"), "EmptyMass:r=10")
        val repo = FlightModelRepository(root)
        assertIs<FlightModelState.Ready>(repo.load("first"))
        assertEquals("second", assertIs<FlightModelState.Missing>(repo.load("second")).aircraft)
        assertEquals("first", assertIs<FlightModelState.Ready>(repo.load("first")).aircraft)
    }
}
