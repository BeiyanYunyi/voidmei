package voidmei.desktop

import voidmei.fm.*
import java.nio.file.*
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

sealed interface FlightModelState {
    data object Unresolved : FlightModelState
    data class Loading(val aircraft: String) : FlightModelState
    data class Ready(val aircraft: String, val source: String, val document: BlkBlock, val central: BlkBlock? = null, val dataDirectory: String? = null) : FlightModelState
    data class Missing(val aircraft: String, val path: String) : FlightModelState
    data class Invalid(val aircraft: String, val reason: String) : FlightModelState
}

/** Called on Dispatchers.IO. Bounded positive/negative cache, explicitly cleared on reload. */
class FlightModelRepository(dataRoot: Path) {
    private val root = dataRoot.toAbsolutePath().normalize()
    private val cache = linkedMapOf<String, FlightModelState>()

    @Synchronized fun clear() = cache.clear()

    fun listAircraft(checkActive: () -> Unit = {}): List<String> {
        checkActive()
        val directory = resolveDirectory().toRealPath()
        val names = mutableListOf<String>()
        Files.newDirectoryStream(directory).use { entries ->
            var count = 0
            for (path in entries) {
                checkActive()
                require(++count <= 20_000) { "机型目录超过 20000 个条目" }
                val file = path.fileName.toString()
                if (!file.matches(Regex("[a-z0-9_-]{1,128}\\.blkx"))) continue
                if (Files.isRegularFile(path) && path.toRealPath().startsWith(directory)) names += file.removeSuffix(".blkx")
            }
        }
        return names.sorted()
    }

    @Synchronized fun load(aircraft: String?, checkActive: () -> Unit = {}): FlightModelState {
        checkActive()
        val name = aircraft?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return FlightModelState.Unresolved
        if (!Regex("[a-z0-9_-]+").matches(name)) return FlightModelState.Invalid(name, "Invalid aircraft identifier")
        cache[name]?.let { return it }
        val result = try {
            val directory = resolveDirectory()
            val centralPath = directory.resolve("$name.blkx")
            if (!Files.exists(centralPath)) FlightModelState.Missing(name, centralPath.toString()) else {
                val central = read(centralPath, directory, checkActive)
                val references = central.entries.filterIsInstance<BlkField>().filter { it.name.equals("fmFile", true) }
                require(references.size <= 1) { "Ambiguous fmFile field" }
                val reference = references.singleOrNull()?.let {
                    require(it.type.equals("t", true) && it.values.size == 1) { "Invalid fmFile field" }
                    it.values.single()
                } ?: "fm/$name.blk"
                val relative = reference.replace('\\', '/').removePrefix("/")
                require(relative.isNotBlank() && ':' !in relative && !relative.startsWith('/')) { "Invalid FM path" }
                val suffix = when {
                    relative.endsWith(".blkx", true) -> relative
                    relative.endsWith(".blk", true) -> relative + "x"
                    else -> "$relative.blkx"
                }
                val physical = directory.resolve(suffix).normalize()
                require(physical.startsWith(directory)) { "FM path escapes flightmodels directory" }
                FlightModelState.Ready(name, directory.relativize(physical).toString(), read(physical, directory, checkActive), central, directory.toString())
            }
        } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (e: Exception) {
            FlightModelState.Invalid(name, e.message ?: "Cannot load FM")
        }
        checkActive()
        if (cache.size >= 16) cache.remove(cache.keys.first())
        cache[name] = result
        return result
    }

    private fun resolveDirectory(): Path {
        val candidates = buildList {
            add(root.resolve("aces/gamedata/flightmodels"))
            add(root.resolve("aces.vromfs.bin_u/gamedata/flightmodels"))
            add(root.resolve("gamedata/flightmodels"))
            if (root.fileName?.toString() == "flightmodels" || Files.isDirectory(root.resolve("fm"))) add(root)
        }
        val existing = candidates.filter { Files.isDirectory(it) }.map { it.toRealPath() }.distinct()
        require(existing.size <= 1) { "发现多个 flightmodels 目录，请直接选择所需目录：${existing.joinToString()}" }
        return existing.singleOrNull() ?: candidates.first()
    }

    private fun read(path: Path, directory: Path, checkActive: () -> Unit): BlkBlock {
        checkActive()
        require(path.toRealPath().startsWith(directory.toRealPath())) { "FM symlink escapes flightmodels directory" }
        require(Files.isRegularFile(path)) { "FM path must be a regular file: $path" }
        Files.newInputStream(path).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 16 * 1024 * 1024) { "FM exceeds 16 MiB" }
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            checkActive()
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return FlightModelDocument.parse(text, checkActive)
        }
    }
}
