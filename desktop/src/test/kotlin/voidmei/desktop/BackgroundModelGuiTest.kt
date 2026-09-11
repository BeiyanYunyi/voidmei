package voidmei.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class BackgroundModelGuiTest {
    @get:Rule val compose = createComposeRule()

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
