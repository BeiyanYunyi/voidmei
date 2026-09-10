package voidmei.desktop

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardOpenOption.*
import voidmei.recording.FlightRecordReader

/** Publish a complete file without replacing an existing name, including a concurrently created one. */
internal fun exportRecordCsv(path: Path, text: String, write: (Path, ByteArray) -> Unit = ::writeRecordData) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    require(bytes.size <= FlightRecordReader.MAX_BYTES) { "导出记录超过 64 MiB 限制" }
    val target = path.toAbsolutePath()
    val temporary = Files.createTempFile(target.parent, ".voidmei-export-", ".tmp")
    try {
        write(temporary, bytes)
        // ATOMIC_MOVE may replace an existing target even without REPLACE_EXISTING.
        // A same-directory hard link publishes the completed inode and must fail if the name exists.
        Files.createLink(target, temporary)
    } finally { Files.deleteIfExists(temporary) }
}

private fun writeRecordData(path: Path, bytes: ByteArray) {
    FileChannel.open(path, TRUNCATE_EXISTING, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
