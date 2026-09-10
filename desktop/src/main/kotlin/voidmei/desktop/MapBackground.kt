package voidmei.desktop

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import voidmei.telemetry.MapBounds
import voidmei.telemetry.MapTelemetryParser

internal fun decodeMapBackground(bytes: ByteArray): BufferedImage = decodeBoundedImage(bytes, "地图底图")

internal fun decodeBoundedImage(bytes: ByteArray, label: String): BufferedImage {
    require(bytes.size <= 8 * 1024 * 1024) { "$label 超过 8 MiB" }
    MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val readers = ImageIO.getImageReaders(input)
        require(readers.hasNext()) { "$label 不是支持的图片" }
        val reader = readers.next()
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 16_000_000) { "$label 像素过大" }
            return requireNotNull(reader.read(0)) { "$label 无法解码" }
        } finally { reader.dispose() }
    }
}

/** Bracket the image request with metadata, just like the object snapshot. */
internal suspend fun loadMapBackground(transport: HttpTelemetryTransport, expected: MapBounds): BufferedImage {
    require(expected.generation != null) { "缺少地图代号，无法确认底图对应" }
    require(MapTelemetryParser.info(transport.get("/map_info.json")) == expected) { "地图已切换" }
    val bytes = transport.getBytes("/map.img", 8 * 1024 * 1024)
    require(MapTelemetryParser.info(transport.get("/map_info.json")) == expected) { "地图已切换" }
    return decodeMapBackground(bytes)
}
