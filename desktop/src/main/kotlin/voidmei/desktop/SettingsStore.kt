package voidmei.desktop

import voidmei.config.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.util.Locale

data class LoadedSettings(val settings: AppSettings, val error: String? = null)

class SettingsStore(val file: Path, private val defaults: AppSettings = AppSettings()) {
    private var canWrite = true
    private var original: String? = null

    private fun readDocument(): String {
        require(Files.isRegularFile(file)) { "配置路径不是普通文件" }
        val bytes = Files.newInputStream(file).use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES) { "配置超过 1 MiB 限制" }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    @Synchronized
    fun load(): LoadedSettings = try {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) LoadedSettings(defaults) else {
            val text = readDocument()
            val settings = SettingsJson.decode(text, defaultHudCompatibilityMode = defaults.hudCompatibilityMode)
            original = text
            LoadedSettings(settings)
        }
    } catch (e: Exception) {
        canWrite = false
        LoadedSettings(defaults, "配置无法读取，本次不保存设置：${e.message}")
    }

    @Synchronized
    fun save(settings: AppSettings) {
        check(canWrite) { "原配置无法读取，已停止写入以保留文件" }
        // Detect edits made by another process rather than overwriting them.
        val disk = if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) null else readDocument()
        check(disk == original) { "配置已被其他进程修改，请重启后再保存" }
        var text = SettingsJson.encode(settings, original)
        var encoded = text.toByteArray(Charsets.UTF_8)
        // Nested layout presets can exceed the document limit through indentation alone.
        // Keep small profiles readable; compact large ones without discarding unknown keys.
        if (encoded.size > MAX_BYTES) {
            text = SettingsJson.encode(settings, original, prettyPrint = false)
            encoded = text.toByteArray(Charsets.UTF_8)
        }
        require(encoded.size <= MAX_BYTES) { "配置超过 1 MiB 限制" }
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, ".settings-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING).use { channel ->
                val bytes = ByteBuffer.wrap(encoded)
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            // If the filesystem cannot atomically replace the document, leave the old file intact.
            Files.move(temporary, file.toAbsolutePath(), ATOMIC_MOVE, REPLACE_EXISTING)
            original = text
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        /** Used only for a new installation (or read-only recovery), never to rewrite saved paths. */
        fun desktopDefaults(
            environment: Map<String, String> = System.getenv(),
            os: String = System.getProperty("os.name"),
            home: Path = Path.of(System.getProperty("user.home")),
        ): AppSettings {
            fun env(key: String) = environment[key]?.takeIf { it.isNotBlank() }?.let(Path::of)
            val root = (env("VOIDMEI_HOME") ?: when {
                os.lowercase(Locale.ROOT).startsWith("windows") ->
                    (env("LOCALAPPDATA") ?: env("APPDATA") ?: home.resolve("AppData/Local")).resolve("voidmei")
                os.lowercase(Locale.ROOT).contains("mac") -> home.resolve("Library/Application Support/voidmei")
                else -> (env("XDG_DATA_HOME")?.takeIf { it.isAbsolute } ?: home.resolve(".local/share")).resolve("voidmei")
            }).toAbsolutePath().normalize()
            return AppSettings(fmDataRoot = root.resolve("data").toString(),
                recordingDirectory = root.resolve("records").toString(), voiceDirectory = root.resolve("voice").toString(),
                hudCompatibilityMode = os.lowercase(Locale.ROOT).contains("linux"))
        }

        fun defaultPath(
            environment: Map<String, String> = System.getenv(),
            os: String = System.getProperty("os.name"),
            home: Path = Path.of(System.getProperty("user.home")),
        ): Path {
            fun env(key: String) = environment[key]?.takeIf { it.isNotBlank() }?.let(Path::of)
            val directory = env("VOIDMEI_CONFIG_HOME") ?: when {
                os.lowercase(Locale.ROOT).startsWith("windows") -> (env("APPDATA") ?: home.resolve("AppData/Roaming")).resolve("voidmei")
                os.lowercase(Locale.ROOT).contains("mac") -> home.resolve("Library/Application Support/voidmei")
                else -> (env("XDG_CONFIG_HOME")?.takeIf { it.isAbsolute } ?: home.resolve(".config")).resolve("voidmei")
            }
            return directory.resolve("settings-kmp.json")
        }
    }
}

/** A single writer serializes updates. Closing drains the final snapshot before the app exits. */
class SettingsWriter(scope: CoroutineScope, private val store: SettingsStore, private val onError: (String) -> Unit) {
    private data class Request(val settings: AppSettings, val result: CompletableDeferred<Boolean>? = null)
    private val updates = Channel<Request>(Channel.CONFLATED)
    private var acceptingUpdates = true
    private var lastSaveSucceeded = true
    private val closeMutex = Mutex()
    private var closed = false
    private val worker = scope.launch {
        for (request in updates) {
            try {
                withContext(Dispatchers.IO) { store.save(request.settings) }
                lastSaveSucceeded = true
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { lastSaveSucceeded = false; onError("配置保存失败：${e.message}") }
            request.result?.complete(lastSaveSucceeded)
        }
    }
    fun update(settings: AppSettings) { if (acceptingUpdates) updates.trySend(Request(settings)) }
    private fun stoppedWorker(): Boolean {
        acceptingUpdates = false
        lastSaveSucceeded = false
        onError("配置保存工作协程已停止，无法保存最后的设置。可选择不保存并退出。")
        return false
    }
    suspend fun close(finalSettings: AppSettings): Boolean = closeMutex.withLock {
        if (closed) return@withLock lastSaveSucceeded
        if (!worker.isActive) return@withLock stoppedWorker()
        acceptingUpdates = false
        try {
            val result = CompletableDeferred<Boolean>()
            if (updates.trySend(Request(finalSettings, result)).isFailure) return@withLock false
            val saved = select {
                result.onAwait { it }
                worker.onJoin { stoppedWorker() }
            }
            if (saved) {
                closed = true
                updates.close()
                worker.join()
            }
            saved
        } finally {
            // A caller may stop waiting while the worker still accepts future saves.
            acceptingUpdates = !closed && worker.isActive
        }
    }

    /** Drop queued snapshots; an atomic file write already in progress must finish before exit. */
    suspend fun discard() = closeMutex.withLock {
        acceptingUpdates = false
        closed = true
        lastSaveSucceeded = false
        updates.cancel()
        worker.cancelAndJoin()
    }
}
