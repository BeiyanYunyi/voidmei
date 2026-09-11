package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import voidmei.config.AppSettings
import voidmei.telemetry.*
import kotlin.test.*

class RealSnapshotHudGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capturedFlightDisplaysSelectedReadingsAtNormalAndNarrowWidths() {
        val source = listOf(Path.of("../script/mock_data.json"), Path.of("script/mock_data.json")).first { Files.exists(it) }
        val before = Files.readAllBytes(source)
        val json = Json.parseToJsonElement(before.toString(Charsets.UTF_8)).jsonObject
        val telemetry = assertNotNull(TelemetryParser.parse(json.getValue("/state").toString(), json.getValue("/indicators").toString()))
        val flight = ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0))
        assertNull(flight.metrics.specificExcessPowerMps) // A snapshot contains no speed history.
        var narrow by mutableStateOf(false)
        val settings = AppSettings(hudFields = listOf("ias", "altitude", "sep", "load", "aoa", "fuel_percent"),
            hudEngineIndex = 1, hudEngineFields = listOf("rpm", "power", "water_temperature", "oil_temperature"),
            hudMechanization = false, hudReadingColumns = 2)
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.requiredSize(if (narrow) 260.dp else 440.dp, if (narrow) 360.dp else 700.dp)) {
                HudPanel(flight, if (narrow) settings.copy(hudFontScale = 1.5f, hudReadingColumns = 1) else settings,
                    emptyList(), null) { Text("真机快照 · HUD") }
            }
        } }
        compose.onNodeWithText("380 km/h").assertIsDisplayed()
        compose.onNodeWithText("— m/s").assertIsDisplayed()
        compose.onNodeWithText("发动机 #1").assertIsDisplayed()
        compose.onNodeWithText("油门").assertDoesNotExist()
        val title = compose.onNodeWithText("发动机 #1").captureToImage().toPixelMap()
        assertTrue((0 until title.height).any { y -> (0 until title.width).any { x ->
            val color = title[x, y]
            color.red > .6f && color.green > .6f && color.blue > .6f && color.alpha > .5f
        } }, "Engine title must be readable on the dark HUD background")
        save("normal")
        compose.runOnIdle { narrow = true }
        compose.onNodeWithText("380 km/h").assertIsDisplayed()
        compose.onNodeWithTag("hud-scroll-indicator").assertExists()
        save("narrow-top")
        compose.onNodeWithText("油温").performScrollTo().assertIsDisplayed()
        save("narrow-engine")
        assertContentEquals(before, Files.readAllBytes(source))
    }

    private fun save(name: String) {
        val pixels = compose.onNodeWithTag("hud-panel").captureToImage().toPixelMap()
        val output = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) output.setRGB(x, y, pixels[x, y].toArgb())
        val directory = Path.of("build/hud-preview")
        Files.createDirectories(directory)
        check(ImageIO.write(output, "png", directory.resolve("real-snapshot-$name.png").toFile()))
    }
}
