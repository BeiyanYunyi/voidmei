package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.test.*
import voidmei.fm.*
import voidmei.telemetry.TelemetryParser

class ModelCalculationGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedCalculationClearsPublishedModelAndAnotherFuelChoiceRecovers() {
        val root = Files.createTempDirectory("voidmei-failed-model")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), """
            fmFile:t="fm/test.blk"
            modifications { ussr_fuel_b-100 { effects { addHorsePowers:i=50 } } }
        """.trimIndent())
        Files.writeString(directory.resolve("fm/test.blkx"), "Mass { EmptyMass:r=2500 }")
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        var published: FlightModelParameters? = null
        var publishedAircraft: String? = null
        var completed = 0
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                FlightModelPanel(telemetry, root.toString(), onModel = { aircraft, parameters ->
                    publishedAircraft = aircraft
                    published = parameters
                    if (parameters != null) completed++
                }, onDataRoot = {}, parameterExtractor = { document, fuel ->
                    if (fuel != null) throw IllegalArgumentException("测试燃油参数损坏")
                    FlightModelExtractor.extract(document, fuel)
                })
            } } }
            compose.waitUntil(10000) { completed == 1 }
            repeat(2) { attempt ->
                compose.onNodeWithText("B-100", substring = false).performScrollTo().performClick()
                compose.waitUntil(5000) {
                    compose.onAllNodesWithText("test 模型参数不可用：测试燃油参数损坏").fetchSemanticsNodes().isNotEmpty()
                }
                compose.runOnIdle { assertNull(published); assertNull(publishedAircraft) }
                compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertDoesNotExist()
                compose.onNodeWithText("不追加修正").performScrollTo().performClick()
                compose.waitUntil(5000) { completed == attempt + 2 }
                compose.runOnIdle { assertEquals("test", publishedAircraft); assertEquals(2500.0, published?.emptyMassKg) }
                compose.onNodeWithText("test 模型参数不可用：测试燃油参数损坏").assertDoesNotExist()
                compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertExists()
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun pendingFuelCalculationCanBeReplacedAndItsLateResultIsIgnored() {
        val root = Files.createTempDirectory("voidmei-pending-model")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), """
            fmFile:t="fm/test.blk"
            modifications { ussr_fuel_b-100 { effects { addHorsePowers:i=50 } } }
        """.trimIndent())
        Files.writeString(directory.resolve("fm/test.blkx"), "Mass { EmptyMass:r=2500 }")
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        val release = CountDownLatch(1)
        val started = AtomicBoolean()
        val finished = AtomicBoolean()
        var published: FlightModelParameters? = null
        var completed = 0
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                FlightModelPanel(telemetry, root.toString(), onModel = { _, parameters ->
                    published = parameters
                    if (parameters != null) completed++
                }, onDataRoot = {}, parameterExtractor = { document, fuel ->
                    check(!SwingUtilities.isEventDispatchThread())
                    if (fuel != null) {
                        started.set(true)
                        check(release.await(15, TimeUnit.SECONDS))
                        finished.set(true)
                    }
                    FlightModelExtractor.extract(document, fuel)
                })
            } } }
            compose.waitUntil(10000) { completed == 1 }
            compose.onNodeWithText("B-100", substring = false).performScrollTo().performClick()
            compose.waitUntil(5000) { started.get() && published == null }
            compose.onNodeWithText("正在计算 test 模型参数…").assertExists()
            compose.onNodeWithText("不追加修正").performScrollTo().performClick()
            compose.waitUntil(5000) { completed == 2 }
            compose.runOnIdle { assertNull(assertNotNull(published).compressorFuel) }
            release.countDown()
            compose.waitUntil(5000) { finished.get() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(2, completed); assertNull(assertNotNull(published).compressorFuel) }
            compose.onNodeWithText("不追加修正").assertIsSelected()
        } finally {
            release.countDown()
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
