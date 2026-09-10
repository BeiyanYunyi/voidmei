package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import voidmei.telemetry.TelemetryParser

class SweptLoadGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun panelUpdatesLoadLimitsWithSweepAndClearsMissingTelemetry() {
        val root = Files.createTempDirectory("voidmei-swept-load")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), "fmFile:t=\"fm/test.blk\"")
        Files.writeString(directory.resolve("fm/test.blkx"), """
            Mass { EmptyMass:r=900; OilMass:r=75; MaxNitro:r=25 }
            Aerodynamics {
                WingPlaneSweep0 { Sweep:r=0; Strength { CritOverload:p2=-49000,98000 } }
                WingPlaneSweep1 { Sweep:r=1; Strength { CritOverload:p2=-49000,147000 } }
            }
        """)
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"type":"test"}""")!!.copy(fuelKg = 1000.0, wingSweepRatio = 0.0))
        var ready = false
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                FlightModelPanel(telemetry, root.toString(), onModel = { _, parameters -> ready = parameters != null }, onDataRoot = {})
            } } }
            compose.waitUntil(10000) { ready }
            compose.onNodeWithText("基础质量估算过载范围 -4.80 ～ 10.80 G").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = 0.5) }
            compose.onNodeWithText("基础质量估算过载范围 -4.80 ～ 13.80 G").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = 1.0) }
            compose.onNodeWithText("基础质量估算过载范围 -4.80 ～ 16.80 G").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = null) }
            compose.onNodeWithText("基础质量估算过载范围 — ～ — G").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = 0.5, fuelKg = 0.0) }
            compose.onNodeWithText("基础质量估算过载范围 -10.80 ～ 28.80 G").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(fuelKg = null) }
            compose.onNodeWithText("基础质量估算过载范围 — ～ — G").assertExists()
        } finally { root.toFile().deleteRecursively() }
    }
}
