package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import voidmei.config.ModelDetailSection

class ModelCurveRetentionGuiTest {
    @get:Rule val compose = createComposeRule()
    private fun withModel(source: String, check: (() -> Unit, (Boolean) -> Unit) -> Unit) {
        val root = Files.createTempDirectory("voidmei-curve-retention")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        for (name in listOf("one", "two")) {
            Files.writeString(directory.resolve("$name.blkx"), "fmFile:t=\"fm/$name.blk\"")
            Files.writeString(directory.resolve("fm/$name.blkx"), source)
        }
        var aircraft by mutableStateOf("one")
        var hidden by mutableStateOf(emptySet<ModelDetailSection>())
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                FlightModelPanel(null, root.toString(), onModel = { _, _ -> }, onDataRoot = {},
                    aircraftOverride = aircraft, hiddenSections = hidden, onHiddenSections = { hidden = it })
            } } }
            check({ compose.runOnIdle { aircraft = "two" } }, { hide ->
                compose.runOnIdle { hidden = if (hide) setOf(ModelDetailSection.PISTON, ModelDetailSection.JET) else emptySet() }
            })
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    private fun waitFor(text: String) = compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    @Test fun pistonRestoresConditionsDraftAndProbeButNewAircraftStartsFresh() = withModel("""
        EngineType0 {
            Main { Type:t=Inline; Power:r=1200; AfterburnerBoost:r=1.2 }
            Compressor { NumSteps:i=1; Altitude0:r=4000; Power0:r=1400; ATA0:r=1.3; AfterburnerManifoldPressure:r=1.6 }
            Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2400 }
        }
        Engine0 { Type:i=0 }
    """) { changeAircraft, hide ->
        waitFor("展开功率曲线"); click("展开功率曲线")
        click("等效空速 EAS"); click("300 km/h"); click("30°C")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("power-curve-altitude").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("power-curve-altitude").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(4000f) }
        compose.onNodeWithText("自定义速度 EAS (km/h)").performScrollTo().performTextReplacement("invalid")
        click("应用功率条件")
        repeat(2) {
            hide(true); compose.onNodeWithTag("power-curve-altitude").assertDoesNotExist(); hide(false)
            waitFor("收起功率曲线")
            compose.onNodeWithText("等效空速 EAS").assertIsSelected()
            compose.onNodeWithText("300 km/h").assertIsSelected()
            compose.onNodeWithText("30°C").assertIsSelected()
            compose.onNodeWithText("自定义速度 EAS (km/h)").assertTextContains("invalid")
            compose.onNodeWithText("速度需为非负有限数，海平面温度需为大于 -273.15°C 的有限数。").assertExists()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("power-curve-altitude").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("power-curve-altitude").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(4000f, 0f..10000f, 399))
        }
        hide(true); changeAircraft(); hide(false); waitFor("展开功率曲线"); click("展开功率曲线")
        compose.onNodeWithText("真空速 TAS").assertIsSelected()
        compose.onNodeWithText("0 km/h").assertIsSelected()
        compose.onNodeWithText("15°C").assertIsSelected()
        compose.onNodeWithText("invalid").assertDoesNotExist()
    }

    @Test fun jetRestoresHeightAndSpeedButNewAircraftStartsFresh() = withModel("""
        EngineType0 {
            Main { Type:t=Jet; AfterburnerBoost:r=1.5 }
            ThrustMax { ThrustMax0:r=1000; Altitude_0:r=0; Altitude_1:r=10000
                Velocity_0:r=0; Velocity_1:r=1000
                ThrustMaxCoeff_0_0:r=1; ThrustMaxCoeff_0_1:r=2
                ThrustMaxCoeff_1_0:r=0.5; ThrustMaxCoeff_1_1:r=1
            }
        }
    """) { changeAircraft, hide ->
        waitFor("展开推力曲线"); click("展开推力曲线")
        compose.onNodeWithTag("jet-curve-altitude").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("jet-curve-speed").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        repeat(2) {
            hide(true); compose.onNodeWithTag("jet-curve-plot").assertDoesNotExist(); hide(false)
            compose.onNodeWithText("收起推力曲线").assertExists()
            compose.onNodeWithText("查看高度 5000 m").assertExists()
            compose.onNodeWithText("速度 500 km/h", substring = true).assertExists()
        }
        hide(true); changeAircraft(); hide(false); waitFor("展开推力曲线"); click("展开推力曲线")
        compose.onNodeWithText("查看高度 0 m").assertExists()
        compose.onNodeWithText("速度 0 km/h", substring = true).assertExists()
    }
}
