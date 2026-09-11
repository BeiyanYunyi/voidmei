package voidmei.desktop

import kotlinx.coroutines.*
import voidmei.config.*
import java.nio.file.Files
import java.nio.file.Path
import java.awt.Rectangle
import kotlin.test.*

class SettingsStoreTest {
    @Test fun fullPresetCollectionPersistsWithinDocumentLimitAndRetainsUnknownKeys() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        Files.writeString(path, """{"version":1,"future":{"value":42}}""")
        val scene = HudSceneLayout(1280, 720, (1..32).map { index ->
            HudRegion("region-$index", HudRegionContent.FLIGHT, 0, 0, 400, 300,
                fields = voidmei.telemetry.HudField.entries.map { it.id }, title = "飞行数据".repeat(20), fontScale = 1.5f,
                showFlightInstruments = false, showFlightStatus = false, messageLimit = 20)
        })
        val settings = AppSettings(hudSceneLayout = scene,
            hudScenePresets = (1..16).associate { "布局 $it" to scene })
        val store = SettingsStore(path)
        assertNull(store.load().error)
        store.save(settings)
        assertTrue(Files.size(path) <= SettingsStore.MAX_BYTES)
        val restarted = SettingsStore(path)
        assertNull(restarted.load().error)
        assertEquals(settings, restarted.load().settings)
        restarted.save(settings.copy(voiceVolume = 25))
        assertEquals(settings.copy(voiceVolume = 25), SettingsStore(path).load().settings)
        val document = kotlinx.serialization.json.Json.parseToJsonElement(Files.readString(path))
        assertTrue(document.toString().contains("\"future\":{\"value\":42}"))
    }

    @Test fun olderProfileWithoutHudChoiceUsesPlatformDefaultAndRetainsOtherFields() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val original = """{"version":1,"hudEnabled":true,"fmDataRoot":"relative-data","future":42}"""
        Files.writeString(path, original)
        val store = SettingsStore(path, SettingsStore.desktopDefaults(emptyMap(), "Linux", directory))
        val loaded = store.load()
        assertNull(loaded.error)
        assertTrue(loaded.settings.hudCompatibilityMode)
        assertTrue(loaded.settings.hudEnabled)
        assertEquals("relative-data", loaded.settings.fmDataRoot)
        assertEquals(original, Files.readString(path))
        store.save(loaded.settings)
        assertTrue(Files.readString(path).contains("\"future\": 42"))
        assertTrue(SettingsStore(path).load().settings.hudCompatibilityMode)
    }

    @Test fun cancelledCloseCallerDoesNotDisableLaterUpdates(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-cancel-save-caller")
        val path = directory.resolve("settings.json")
        val store = SettingsStore(path)
        store.load()
        val writer = SettingsWriter(this, store) { fail(it) }
        try {
            val closing = launch(start = CoroutineStart.UNDISPATCHED) {
                writer.close(AppSettings(voiceVolume = 80))
            }
            closing.cancelAndJoin()
            writer.update(AppSettings(voiceVolume = 90))
            withTimeout(5000) {
                while (!Files.exists(path) || SettingsStore(path).load().settings.voiceVolume != 90) delay(10)
            }
            assertTrue(writer.close(AppSettings(voiceVolume = 90)))
        } finally { writer.discard(); Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun newLinuxProfilesUseCompatibleHudWithoutOverridingSavedChoice() = withDirectory { directory ->
        val defaults = SettingsStore.desktopDefaults(emptyMap(), "Linux", directory)
        assertTrue(defaults.hudCompatibilityMode)
        assertFalse(defaults.hudEnabled)
        for (os in listOf("Windows 11", "Mac OS X"))
            assertFalse(SettingsStore.desktopDefaults(emptyMap(), os, directory).hudCompatibilityMode)
        val store = SettingsStore(directory.resolve("settings.json"), defaults)
        assertTrue(store.load().settings.hudCompatibilityMode)
        store.save(defaults.copy(hudCompatibilityMode = false))
        assertFalse(SettingsStore(store.file, defaults).load().settings.hudCompatibilityMode)
    }

    @Test fun excessiveNestingEntersReadOnlyRecoveryWithoutTouchingTheDocument() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val text = "{\"version\":1,\"future\":" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}"
        Files.writeString(path, text)
        val store = SettingsStore(path)
        assertNotNull(store.load().error)
        assertFails { store.save(AppSettings()) }
        assertEquals(text, Files.readString(path))
    }

    @Test fun oversizedOrInvalidUtf8DocumentsRemainUntouched() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        for (bytes in listOf(ByteArray(SettingsStore.MAX_BYTES + 1) { 32 }, byteArrayOf(0xC3.toByte(), 0x28))) {
            Files.write(path, bytes)
            val store = SettingsStore(path)
            assertNotNull(store.load().error)
            assertFails { store.save(AppSettings()) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    @Test fun exactReadLimitIsAcceptedButMultibyteOutputCannotExceedIt() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val exact = "{\"version\":1}".padEnd(SettingsStore.MAX_BYTES)
        Files.writeString(path, exact)
        val store = SettingsStore(path)
        assertNull(store.load().error)
        assertFailsWith<IllegalArgumentException> {
            store.save(AppSettings(hudFields = listOf("重".repeat(SettingsStore.MAX_BYTES / 2))))
        }
        assertEquals(exact, Files.readString(path))
        Files.list(directory).use { assertEquals(1, it.count()) }
        store.save(AppSettings(hudEnabled = true))
        assertTrue(SettingsStore(path).load().settings.hudEnabled)
    }

    @Test fun conflictCheckAlsoBoundsExternallyReplacedDocuments() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val store = SettingsStore(path)
        store.load()
        store.save(AppSettings())
        val changed = ByteArray(SettingsStore.MAX_BYTES + 1) { 32 }
        Files.write(path, changed)
        assertFailsWith<IllegalArgumentException> { store.save(AppSettings(hudEnabled = true)) }
        assertContentEquals(changed, Files.readAllBytes(path))
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("voidmei-settings-test")
        try { test(directory) } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun saveAndRestartRestoresSettingsWithoutTemporaryFiles() = withDirectory { directory ->
        val path = directory.resolve("config/settings-kmp.json")
        val store = SettingsStore(path)
        assertEquals(AppSettings(), store.load().settings)
        val expected = AppSettings(hudEnabled = true, pollIntervalMs = 500)
        store.save(expected)
        assertEquals(expected, SettingsStore(path).load().settings)
        Files.list(path.parent).use { assertEquals(1, it.count()) }
    }

    @Test fun corruptOrFutureConfigIsNeverOverwritten() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        for (text in listOf("truncated {", """{"version":99}""")) {
            Files.writeString(path, text)
            val store = SettingsStore(path)
            assertNotNull(store.load().error)
            assertFailsWith<IllegalStateException> { store.save(AppSettings()) }
            assertEquals(text, Files.readString(path))
        }
    }

    @Test fun detectsAnotherWriterWithoutClobberingItsChanges() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val first = SettingsStore(path)
        val second = SettingsStore(path)
        first.load(); second.load()
        val updated = AppSettings(hudEnabled = true)
        first.save(updated)
        assertFailsWith<IllegalStateException> { second.save(AppSettings()) }
        assertEquals(updated, SettingsStore(path).load().settings)
    }

    @Test fun writerDrainsFinalSnapshotBeforeClose(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-test")
        val path = directory.resolve("settings.json")
        try {
            val store = SettingsStore(path)
            store.load()
            val errors = mutableListOf<String>()
            val writer = SettingsWriter(this, store) { errors += it }
            repeat(100) { writer.update(AppSettings(pollIntervalMs = (100 + it).toLong())) }
            val final = AppSettings(pollIntervalMs = 500, hudEnabled = true)
            assertTrue(writer.close(final))
            assertEquals(final, SettingsStore(path).load().settings)
            assertTrue(errors.isEmpty())
        } finally { Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun writerReportsFinalSaveFailureInsteadOfClaimingSuccess(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-error-test")
        val path = directory.resolve("settings.json")
        try {
            val store = SettingsStore(path)
            store.load()
            Files.writeString(path, "external change")
            val errors = mutableListOf<String>()
            val writer = SettingsWriter(this, store) { errors += it }
            assertFalse(writer.close(AppSettings()))
            assertEquals(1, errors.size)
            assertEquals("external change", Files.readString(path))
            Files.delete(path)
            assertTrue(writer.close(AppSettings(hudEnabled = true)))
            assertTrue(SettingsStore(path).load().settings.hudEnabled)
        } finally { Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun concurrentCloseCallsFinishWithoutReplacingThePendingFinalSnapshot(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-concurrent")
        val path = directory.resolve("settings.json")
        try {
            val store = SettingsStore(path)
            store.load()
            val writer = SettingsWriter(this, store) { fail(it) }
            val firstSettings = AppSettings(hudFontScale = 1.5f)
            // Both callers reach close before the worker can consume its conflated queue.
            val first = async(start = CoroutineStart.UNDISPATCHED) { writer.close(firstSettings) }
            val second = async(start = CoroutineStart.UNDISPATCHED) { writer.close(AppSettings(hudFontScale = 2f)) }
            withTimeout(5000) { assertTrue(first.await()); assertTrue(second.await()) }
            assertEquals(firstSettings, SettingsStore(path).load().settings)
        } finally { Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun discardDropsQueuedSettingsAndRefusesLaterWrites(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-discard")
        val path = directory.resolve("settings.json")
        try {
            val store = SettingsStore(path)
            store.load()
            store.save(AppSettings())
            val original = Files.readString(path)
            val writer = SettingsWriter(this, store) { fail(it) }
            writer.update(AppSettings(hudFontScale = 2f))
            writer.discard()
            writer.update(AppSettings(hudFontScale = 0.75f))
            assertFalse(writer.close(AppSettings(hudEnabled = true)))
            writer.discard()
            assertEquals(original, Files.readString(path))
        } finally { Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun cancelledWorkerDoesNotLeaveFinalSaveWaitingForever(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-cancel")
        val path = directory.resolve("settings.json")
        val owner = SupervisorJob()
        try {
            val store = SettingsStore(path)
            store.load()
            val errors = mutableListOf<String>()
            val writer = SettingsWriter(CoroutineScope(coroutineContext + owner), store) { errors += it }
            val closing = async(start = CoroutineStart.UNDISPATCHED) { writer.close(AppSettings()) }
            owner.cancelAndJoin()
            withTimeout(5000) { assertFalse(closing.await()) }
            assertTrue(errors.single().contains("工作协程已停止"))
            assertFalse(writer.close(AppSettings()))
            assertEquals(2, errors.size)
            writer.discard()
            assertFalse(Files.exists(path))
        } finally { owner.cancel(); Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun workerAlreadyStoppedReportsExitFailureAndPreservesSavedSettings(): Unit = runBlocking {
        val directory = Files.createTempDirectory("voidmei-writer-stopped")
        val path = directory.resolve("settings.json")
        val owner = SupervisorJob()
        try {
            val store = SettingsStore(path)
            store.load()
            store.save(AppSettings(hudEnabled = false))
            val original = Files.readString(path)
            val errors = mutableListOf<String>()
            val writer = SettingsWriter(CoroutineScope(coroutineContext + owner), store) { errors += it }
            owner.cancelAndJoin()
            withTimeout(5000) { assertFalse(writer.close(AppSettings(hudEnabled = true))) }
            assertTrue(errors.single().contains("不保存并退出"))
            writer.update(AppSettings(hudEnabled = true))
            writer.discard()
            assertEquals(original, Files.readString(path))
        } finally { owner.cancel(); Files.deleteIfExists(path); Files.delete(directory) }
    }

    @Test fun pathsFollowPlatformConventionsAndOverride() {
        val home = Path.of("/users/test")
        assertEquals(home.resolve(".config/voidmei/settings-kmp.json"), SettingsStore.defaultPath(emptyMap(), "Linux", home))
        assertEquals(home.resolve("Library/Application Support/voidmei/settings-kmp.json"), SettingsStore.defaultPath(emptyMap(), "Mac OS X", home))
        assertEquals(Path.of("/custom/settings-kmp.json"), SettingsStore.defaultPath(mapOf("VOIDMEI_CONFIG_HOME" to "/custom"), "Linux", home))
        assertEquals(Path.of("/roaming/voidmei/settings-kmp.json"), SettingsStore.defaultPath(mapOf("APPDATA" to "/roaming"), "Windows 11", home))
        assertEquals(home.resolve(".config/voidmei/settings-kmp.json"), SettingsStore.defaultPath(mapOf("XDG_CONFIG_HOME" to "relative"), "Linux", home))
    }

    @Test fun newInstallResourcePathsUseUserDataLocationsAndExplicitWorkspace() {
        val home = Path.of("/users/test")
        val cases = listOf(
            Triple("Linux", emptyMap(), home.resolve(".local/share/voidmei")),
            Triple("Linux", mapOf("XDG_DATA_HOME" to "/user-data"), Path.of("/user-data/voidmei")),
            Triple("Linux", mapOf("XDG_DATA_HOME" to "relative"), home.resolve(".local/share/voidmei")),
            Triple("Mac OS X", emptyMap(), home.resolve("Library/Application Support/voidmei")),
            Triple("Windows 11", mapOf("LOCALAPPDATA" to "/local"), Path.of("/local/voidmei")),
            Triple("Windows 11", emptyMap(), home.resolve("AppData/Local/voidmei")),
            Triple("Linux", mapOf("VOIDMEI_HOME" to "/workspace", "XDG_DATA_HOME" to "/ignored"), Path.of("/workspace")),
        )
        for ((os, environment, root) in cases) {
            val settings = SettingsStore.desktopDefaults(environment, os, home)
            val absolute = root.toAbsolutePath().normalize()
            assertEquals(absolute.resolve("records").toString(), settings.recordingDirectory)
            assertEquals(absolute.resolve("data").toString(), settings.fmDataRoot)
            assertEquals(absolute.resolve("voice").toString(), settings.voiceDirectory)
        }
    }

    @Test fun desktopDefaultsApplyOnlyWhenNoReadableSettingsExist() = withDirectory { directory ->
        val path = directory.resolve("settings.json")
        val defaults = SettingsStore.desktopDefaults(mapOf("VOIDMEI_HOME" to directory.toString()))
        val store = SettingsStore(path, defaults)
        assertEquals(defaults, store.load().settings)
        val custom = AppSettings(recordingDirectory = "existing-relative-records")
        store.save(custom)
        assertEquals(custom, SettingsStore(path, defaults).load().settings)
        Files.writeString(path, "broken config")
        val recovery = SettingsStore(path, defaults)
        assertEquals(defaults, recovery.load().settings)
        assertFails { recovery.save(defaults) }
        assertEquals("broken config", Files.readString(path))
    }

    @Test fun unpluggedDisplayFallsBackButNegativeMonitorCoordinatesAreValid() {
        val main = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(-1920, 0, 1920, 1080)
        val left = WindowPosition(-1500f, 100f)
        assertEquals(left, visiblePosition(left, listOf(main, secondary)))
        assertNull(visiblePosition(left, listOf(main)))
        assertNull(visiblePosition(WindowPosition(1900f, 1070f), listOf(main)))
        assertNull(visiblePosition(null, listOf(main)))
    }
}
