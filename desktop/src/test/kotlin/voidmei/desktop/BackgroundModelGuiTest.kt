package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import voidmei.fm.FlightModelExtractor
import voidmei.telemetry.TelemetryParser
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class BackgroundModelGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun panelFuelSelectionUpdatesSharedModelAndSurvivesPanelRemoval() {
        val root = Files.createTempDirectory("voidmei-shared-model")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), """
            fmFile:t="fm/test.blk"
            modifications { ussr_fuel_b-100 { effects { addHorsePowers:i=50 } } }
        """.trimIndent())
        Files.writeString(directory.resolve("fm/test.blkx"), "Mass { EmptyMass:r=2500 }")
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        val calculations = AtomicInteger()
        var visible by mutableStateOf(true)
        var session: FlightModelSession? = null
        try {
            compose.setContent {
                val shared = rememberFlightModelSession(telemetry.aircraft, root.toString()) { document, fuel ->
                    calculations.incrementAndGet()
                    FlightModelExtractor.extract(document, fuel)
                }
                session = shared
                if (visible) MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    FlightModelPanel(telemetry, root.toString(), onModel = { _, _ -> }, onDataRoot = {},
                        session = shared, parameterExtractor = { _, _ -> error("Panel must use the shared calculation") })
                } }
            }
            compose.waitUntil(10000) { session?.alertModel != null }
            compose.onNodeWithText("B-100").performScrollTo().performClick()
            compose.waitUntil(10000) { session?.alertModel?.parameters?.compressorFuel?.id == "ussr_fuel_b-100" }
            compose.runOnIdle {
                assertEquals(50.0, session?.alertModel?.parameters?.compressorFuel?.addedHorsepower)
                assertEquals(2, calculations.get())
                visible = false
            }
            compose.onNodeWithText("气动模型文件").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("ussr_fuel_b-100", session?.fuelId)
                assertNotNull(session?.alertModel)
                visible = true
            }
            compose.onNodeWithText("B-100").assertIsSelected()
            compose.runOnIdle { assertEquals(2, calculations.get()) }
            compose.onNodeWithText("不追加修正").performScrollTo().performClick()
            compose.waitUntil(10000) { session?.alertModel != null && session?.alertModel?.parameters?.compressorFuel == null }
            compose.runOnIdle { assertEquals(3, calculations.get()) }
            compose.onNodeWithText("重新加载").performScrollTo().performClick()
            compose.waitUntil(10000) { calculations.get() == 4 && session?.alertModel != null }
            compose.runOnIdle { assertNull(session?.fuelId) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun loadsSwitchesAndReloadsModelsWithoutRenderingTheSettingsPanel() {
        val root = Files.createTempDirectory("voidmei-background-model")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        for ((name, mass) in listOf("first" to 2500, "second" to 4000)) {
            Files.writeString(directory.resolve("$name.blkx"), "fmFile:t=\"fm/$name.blk\"")
            Files.writeString(directory.resolve("fm/$name.blkx"), "Mass { EmptyMass:r=$mass }")
        }
        var aircraft by mutableStateOf<String?>("first")
        var dataRoot by mutableStateOf(root.toString())
        var session: FlightModelSession? = null
        try {
            // Same application-level owner as Main; no Window or FlightModelPanel is composed.
            compose.setContent { session = rememberFlightModelSession(aircraft, dataRoot) }
            compose.waitUntil(10000) { session?.alertModel?.aircraft == "first" }
            compose.runOnIdle { assertEquals(2500.0, session?.alertModel?.parameters?.emptyMassKg); aircraft = "second" }
            compose.waitUntil(10000) { session?.alertModel?.aircraft == "second" }
            compose.runOnIdle { assertEquals(4000.0, session?.alertModel?.parameters?.emptyMassKg); aircraft = null }
            compose.waitUntil(5000) { session?.state == FlightModelState.Unresolved }
            compose.runOnIdle { assertNull(session?.alertModel); aircraft = "missing" }
            compose.waitUntil(10000) { session?.state is FlightModelState.Missing }
            compose.runOnIdle { assertNull(session?.alertModel); aircraft = "first" }
            compose.waitUntil(10000) { session?.alertModel?.aircraft == "first" }
            Files.writeString(directory.resolve("fm/first.blkx"), "Mass { EmptyMass:r=3000 }")
            compose.runOnIdle { session!!.reload() }
            compose.waitUntil(10000) { session?.alertModel?.parameters?.emptyMassKg == 3000.0 }
            compose.runOnIdle { dataRoot = root.resolve("absent").toString() }
            compose.waitUntil(10000) { session?.state is FlightModelState.Missing || session?.state is FlightModelState.Invalid }
            compose.runOnIdle { assertNull(session?.alertModel) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
