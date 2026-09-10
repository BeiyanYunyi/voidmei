package voidmei.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import voidmei.telemetry.MapTelemetryParser
import kotlin.test.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class MapBackgroundTest {
    @Test fun syntheticMockMapsDecodeWithMatchingBoundsAndOrientation() {
        for ((name, dimensions, topLeft) in listOf(
            Triple("map_wide", 128 to 64, 0x224664), Triple("map_tall", 64 to 128, 0x326941))) {
            val root = Json.parseToJsonElement(Files.readString(Path.of("../script/mock_scenarios/snapshots/$name.json"))).jsonObject
            val image = decodeMapBackground(Base64.getDecoder().decode(root.getValue("/map.img").jsonObject.getValue("base64").jsonPrimitive.content))
            val bounds = assertNotNull(MapTelemetryParser.info(root.getValue("/map_info.json").toString()))
            assertEquals(dimensions.first, image.width)
            assertEquals(dimensions.second, image.height)
            assertEquals(topLeft, image.getRGB(0, 0) and 0xffffff)
            assertEquals(image.width.toDouble() / image.height, bounds.widthM / bounds.heightM)
        }
    }

    private fun png(width: Int = 2) = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    @Test fun imageDimensionsAreCheckedBeforeRasterAllocation() {
        assertEquals(2, decodeMapBackground(png()).width)
        assertFails { decodeMapBackground(png(4097)) }
        assertFails { decodeMapBackground("not an image".encodeToByteArray()) }
        assertFails { decodeMapBackground(ByteArray(8 * 1024 * 1024 + 1)) }
    }

    @Test fun metadataBracketsImageAndRejectsChangingMap(): Unit = runBlocking {
        fun info(generation: Int) = """{"valid":true,"map_min":[0,0],"map_max":[1000,1000],"map_generation":$generation}"""
        var generation = 1
        var switchOnImage = false
        val image = png()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/map_info.json") { e ->
            val bytes = info(generation).encodeToByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }
        server.createContext("/map.img") { e ->
            if (switchOnImage) generation++
            e.sendResponseHeaders(200, image.size.toLong()); e.responseBody.use { it.write(image) }
        }
        server.start()
        try {
            HttpTelemetryTransport("http://127.0.0.1:${server.address.port}").use { transport ->
                val expected = MapTelemetryParser.info(info(1))!!
                assertEquals(2, loadMapBackground(transport, expected).height)
                switchOnImage = true
                assertFails { loadMapBackground(transport, expected) }
                assertFails { loadMapBackground(transport, expected.copy(generation = null)) }
            }
        } finally { server.stop(0) }
    }
}
