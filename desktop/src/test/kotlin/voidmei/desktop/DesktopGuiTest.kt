package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import voidmei.config.AppSettings
import voidmei.telemetry.*
import java.nio.file.Files

class DesktopGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun crosshairRightAlignmentKeepsOversizedArmsInsideWindow() {
        var right by mutableStateOf(false)
        var size by mutableStateOf(80)
        compose.setContent { MaterialTheme { CrosshairPanel(size, Modifier.requiredSize(300.dp, 200.dp), right) } }
        fun bounds(): List<Int> {
            val p = compose.onNodeWithTag("hud-crosshair").captureToImage().toPixelMap()
            val points = buildList {
                for (y in 0 until p.height) for (x in 0 until p.width) {
                    val c = p[x, y]
                    if (c.red > .3f && c.green > c.red * .7f && c.blue < .2f) add(x to y)
                }
            }
            assertTrue(points.isNotEmpty())
            val b = listOf(points.minOf { it.first }, points.maxOf { it.first }, points.minOf { it.second }, points.maxOf { it.second })
            assertTrue(b[0] > 0 && b[1] < p.width - 1 && b[2] > 0 && b[3] < p.height - 1, "Gold arms must fit inside shadow margin: $b")
            return b
        }
        val centered = bounds()
        compose.runOnIdle { right = true }
        val aligned = bounds()
        assertTrue(aligned[0] > centered[0])
        assertTrue(kotlin.math.abs((centered[1] - centered[0]) - (aligned[1] - aligned[0])) <= 1)
        assertTrue(kotlin.math.abs(centered[2] - aligned[2]) <= 1)
        assertTrue(kotlin.math.abs(centered[3] - aligned[3]) <= 1)
        compose.runOnIdle { size = 400 }
        bounds()
    }

    @Test fun crosshairRendersOpenCenterAndTracksHudVisibility() {
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false,
            hudMechanization = false, hudCrosshair = true))
        var state by mutableStateOf<ConnectionState>(ConnectionState.Flying(
            TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!, FlightMetrics()))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(300.dp)) {
            HudPanel(state, settings, emptyList(), null) {}
        } } }
        fun span(): Int {
            val pixels = compose.onNodeWithTag("hud-crosshair").captureToImage().toPixelMap()
            fun gold(x: Int, y: Int): Boolean { val c = pixels[x, y]; return c.red > .9f && c.green > .7f && c.blue < .2f }
            assertFalse(gold(pixels.width / 2, pixels.height / 2), "Center must remain open")
            val xs = buildList { for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (gold(x, y)) add(x) }
            assertTrue(xs.size > 50)
            return xs.max() - xs.min()
        }
        val initial = span()
        compose.runOnIdle { settings = settings.copy(hudCrosshairSizeDp = 80) }
        assertTrue(span() < initial * .6)
        compose.runOnIdle { settings = settings.copy(hudCrosshair = false) }
        compose.onNodeWithTag("hud-crosshair").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudCrosshair = true); state = ConnectionState.WaitingForFlight }
        compose.onNodeWithTag("hud-crosshair").assertDoesNotExist()
    }

    @Test fun hidingLabelsKeepsReadingsAlignedToTheCellRightEdge() {
        var hidden by mutableStateOf(emptySet<Int>())
        compose.setContent { MaterialTheme { Box(Modifier.requiredWidth(200.dp)) {
            FlightReadings(listOf("IAS" to "123456789 km/h"), compact = true, hiddenLabels = hidden)
        } } }
        val right = compose.onNodeWithText("123456789 km/h").fetchSemanticsNode().boundsInRoot.right
        compose.runOnIdle { hidden = setOf(0) }
        val hiddenRight = compose.onNodeWithText("123456789 km/h").fetchSemanticsNode().boundsInRoot.right
        assertEquals(right, hiddenRight, 1f)
    }

    @Test fun hudLabelPreferenceReachesFlightReadingsAndMissingValues() {
        var settings by mutableStateOf(AppSettings(hudFields = listOf("ias"), hudHiddenLabels = listOf("ias"),
            hudAttitude = false, hudMechanization = false, hudFontScale = 2f))
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":450}""", """{"valid":true}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(240.dp, 300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) {}
        } } }
        compose.onNodeWithText("IAS").assertDoesNotExist()
        compose.onNodeWithText("450 km/h").assertIsDisplayed()
        compose.onNodeWithContentDescription("IAS").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(iasKmh = null) }
        compose.onNodeWithText("— km/h").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudHiddenLabels = emptyList()) }
        compose.onNodeWithText("IAS").assertExists()
    }

    @Test fun hiddenReadingLabelsKeepValuesAndWarnings() {
        var hidden by mutableStateOf(setOf(0))
        compose.setContent { MaterialTheme { FlightReadings(listOf("IAS" to "450 km/h", "高度" to "1000 m"),
            compact = true, warningRows = mapOf(0 to "速度预警"), hiddenLabels = hidden) } }
        compose.onNodeWithText("IAS").assertDoesNotExist()
        compose.onNodeWithText("450 km/h").assertExists()
        compose.onNodeWithText("高度").assertExists()
        compose.onNodeWithContentDescription("IAS").assertExists()
        compose.onNodeWithText("450 km/h").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "速度预警"))
        compose.runOnIdle { hidden = emptySet() }
        compose.onNodeWithText("IAS").assertExists()
        compose.onNodeWithText("450 km/h").assertExists()
    }

    @Test fun compassArrowPixelsFollowNorthUpAndHeadingUpModes() {
        var heading by mutableStateOf(90.0)
        var headingUp by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.requiredWidth(240.dp)) { CompassPanel(heading, headingUp) } } }
        fun assertArrow(direction: String) {
            val pixels = compose.onNodeWithTag("hud-compass").captureToImage().toPixelMap()
            var minX = pixels.width; var maxX = -1
            var minY = pixels.height; var maxY = -1
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.85f && color.green > 0.6f && color.blue < 0.65f && color.alpha > 0.9f) {
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    minY = minOf(minY, y); maxY = maxOf(maxY, y); count++
                }
            }
            assertTrue(count > 30, "Expected a visible gold arrow, found $count pixels")
            val cx = pixels.width / 2f; val cy = pixels.height / 2f
            when (direction) {
                "east" -> { assertTrue(maxX - minX > 3 * (maxY - minY)); assertTrue(maxX - cx > 2 * (cx - minX)) }
                "west" -> { assertTrue(maxX - minX > 3 * (maxY - minY)); assertTrue(cx - minX > 2 * (maxX - cx)) }
                "north" -> { assertTrue(maxY - minY > 3 * (maxX - minX)); assertTrue(cy - minY > 2 * (maxY - cy)) }
                else -> error("Unknown direction")
            }
        }
        assertArrow("east")
        compose.runOnIdle { headingUp = true }
        assertArrow("north")
        compose.runOnIdle { heading = 270.0 }
        assertArrow("north")
        compose.runOnIdle { headingUp = false }
        assertArrow("west")
        compose.runOnIdle { heading = 360.0 }
        assertArrow("north")
    }

    @Test fun headingFieldCompassNormalizesAndClearsWithoutAttitude() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"compass":90}""")!!)
        var settings by mutableStateOf(AppSettings(hudFields = listOf("heading"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(300.dp, 350.dp)) {
            HudPanel(ConnectionState.Flying(t, FlightMetrics()), settings, emptyList(), null) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithContentDescription("固定北向罗盘，航向 90°").assertIsDisplayed()
        compose.onNodeWithText("90 °").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudCompassHeadingUp = true) }
        compose.onNodeWithContentDescription("航向朝上罗盘，航向 90°").assertIsDisplayed()
        compose.onNodeWithText("90 °").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudCompassHeadingUp = false) }
        compose.runOnIdle { t = t.copy(headingDeg = -90.0) }
        compose.onNodeWithContentDescription("固定北向罗盘，航向 270°").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(headingDeg = 720.0) }
        compose.onNodeWithContentDescription("固定北向罗盘，航向 0°").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(headingDeg = null) }
        compose.onNodeWithTag("hud-compass").assertDoesNotExist()
        compose.onNodeWithText("— °").assertIsDisplayed()
    }

    @Test fun narrowHudKeepsAllModelBarsScrollableAtLargeFontScale() {
        val t = TelemetryParser.parse("""{"valid":true,"IAS, km/h":400,"M":0.5,"Mfuel, kg":500,"AoA, deg":5,"flaps, %":40,"throttle 1, %":110}""",
            """{"valid":true,"type":"test"}""")!!
        val p = voidmei.fm.FlightModelParameters(null, null,
            listOf(voidmei.fm.WingConfiguration(0.0, 500.0, 1.0, -10.0, 20.0, -5.0, 10.0)), false, emptyList(),
            flapLimits = voidmei.fm.FlapLimits(listOf(voidmei.fm.FlapLimitPoint(0.2, 450.0), voidmei.fm.FlapLimitPoint(1.0, 200.0))),
            controlSpeeds = voidmei.fm.ControlEffectiveSpeeds(250.0, null, 400.0), basicMassKg = 1500.0,
            stallSpeed = voidmei.fm.StallSpeedModel(1500.0, listOf(voidmei.fm.StallLiftProfile(0.0, 10.0, 20.0))))
        var closed = false
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(240.dp, 300.dp)) {
            HudPanel(ConnectionState.Flying(t, FlightMetrics()),
                AppSettings(hudFields = listOf("aoa", "engine1_throttle", "fuel_mass_share", "speed_limit_ratio"),
                    hudFontScale = 2f, hudAttitude = false, hudGear = false, hudAirbrake = false, hudFlaps = false),
                emptyList(), AircraftAlertModel("test", p)) {
                androidx.compose.material3.TextButton(onClick = { closed = true }) { androidx.compose.material3.Text("关闭") }
            }
        } } }
        val header = compose.onNodeWithTag("hud-header").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("hud-panel").getUnclippedBoundsInRoot()
        for (tag in listOf("aoa-margin-bar", "hud-throttle-bar", "fuel-mass-share-bar", "speed-limit-bar", "flap-position-bar")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val bar = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bar.left >= panel.left && bar.right <= panel.right, "$tag exceeds panel width: $bar")
            assertEquals(header, compose.onNodeWithTag("hud-header").getUnclippedBoundsInRoot())
        }
        compose.onNodeWithText("襟翼开度条 · 40.0%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun fuelMassShareHudShowsUncappedValueAndClearsMissingModel() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"Mfuel, kg":500}""", """{"valid":true,"type":"test"}""")!!)
        val model = AircraftAlertModel("test", voidmei.fm.FlightModelParameters(null, null, emptyList(), false, emptyList(), basicMassKg = 1500.0))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(500.dp, 300.dp)) {
            HudPanel(ConnectionState.Flying(t, FlightMetrics()), AppSettings(hudFields = listOf("fuel_mass_share"), hudAttitude = false, hudMechanization = false), emptyList(), model) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithText("25.0 %").assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.5f, 0f..1f))
        compose.runOnIdle { t = t.copy(fuelKg = 4500.0) }
        compose.onNodeWithText("75.0 %").assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { t = t.copy(aircraft = "other") }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithTag("fuel-mass-share-bar").assertDoesNotExist()
    }

    @Test fun flapBarUsesCurrentPositionAndClearsUnknownModelLimit() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":300,"flaps, %":40}""",
            """{"valid":true,"type":"test"}""")!!)
        val limits = voidmei.fm.FlapLimits(listOf(voidmei.fm.FlapLimitPoint(0.2, 400.0), voidmei.fm.FlapLimitPoint(1.0, 200.0)))
        val model = AircraftAlertModel("test", voidmei.fm.FlightModelParameters(null, null, emptyList(), false, emptyList(), flapLimits = limits))
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            HudPanel(ConnectionState.Flying(t, FlightMetrics()), settings, emptyList(), model) { androidx.compose.material3.Text("HUD") }
        } } }
        compose.onNodeWithTag("flap-position-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.4f, 0f..1f))
        compose.onNodeWithContentDescription("襟翼开度；表内最大开度 60.0%").assertExists()
        compose.runOnIdle { settings = settings.copy(hudFlapBar = false) }
        compose.onNodeWithTag("flap-position-bar").assertDoesNotExist()
        compose.onNodeWithText("起落架 —%   襟翼 40.0%   减速板 —%").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFlapBar = true) }
        compose.onNodeWithTag("flap-position-bar").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(iasKmh = 450.0, flapsPercent = 100.0) }
        compose.onNodeWithContentDescription("襟翼开度；模型最大开度未知").assertExists()
        compose.onNodeWithTag("flap-position-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { t = t.copy(flapsPercent = 0.0) }
        compose.onNodeWithTag("flap-position-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle { t = t.copy(flapsPercent = null) }
        compose.onNodeWithTag("flap-position-bar").assertDoesNotExist()
        compose.runOnIdle { t = t.copy(flapsPercent = 20.0); settings = settings.copy(hudFlaps = false, hudGear = false, hudAirbrake = false) }
        compose.onNodeWithTag("flap-position-bar").assertIsDisplayed()
        compose.onNodeWithText("襟翼 20.0%", substring = true).assertDoesNotExist()
        compose.onNodeWithText("襟翼开度条 · 20.0%").assertIsDisplayed()
        compose.onNodeWithText("表内最大开度 —%").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFlapBar = false) }
        compose.onNodeWithText("襟翼开度条 · 20.0%").assertDoesNotExist()
        compose.onNodeWithTag("flap-position-bar").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFlapBar = true, hudMechanization = false) }
        compose.onNodeWithTag("flap-position-bar").assertDoesNotExist()
    }

    @Test fun speedLimitHudShowsScaleMarkersAndClearsStaleModel() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":400,"M":0.5,"Mfuel, kg":0,"flaps, %":0}""",
            """{"valid":true,"type":"test"}""")!!)
        val wing = voidmei.fm.WingConfiguration(0.0, 500.0, 1.0, null, null, null, null)
        val model = AircraftAlertModel("test", voidmei.fm.FlightModelParameters(null, null, listOf(wing), false, emptyList(),
            controlSpeeds = voidmei.fm.ControlEffectiveSpeeds(250.0, null, 400.0),
            stallSpeed = voidmei.fm.StallSpeedModel(1000.0, listOf(voidmei.fm.StallLiftProfile(0.0, 10.0, 20.0)))))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 500.dp)) {
            HudPanel(ConnectionState.Flying(t, FlightMetrics()),
                AppSettings(hudFields = listOf("speed_limit_ratio"), hudAttitude = false, hudMechanization = false), emptyList(), model) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithText("80.0 %").assertIsDisplayed()
        compose.onNodeWithText("基础质量失速 28.8%").assertIsDisplayed()
        compose.onNodeWithText("IAS 限制主导").assertIsDisplayed()
        compose.onNodeWithText("副翼舵效衰减 50.0%").assertIsDisplayed()
        compose.onNodeWithTag("speed-limit-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.8f, 0f..1f))
        compose.runOnIdle { t = t.copy(mach = 1.25) }
        compose.onNodeWithText("125.0 %").assertIsDisplayed()
        compose.onNodeWithText("基础质量失速 45.0%").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(fuelKg = null) }
        compose.onNodeWithText("基础质量失速", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("speed-limit-bar").assertIsDisplayed()
        compose.onNodeWithText("Mach 限制主导").assertIsDisplayed()
        compose.onNodeWithTag("speed-limit-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { t = t.copy(mach = 0.0, iasKmh = 0.0) }
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(aircraft = "other") }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithTag("speed-limit-bar").assertDoesNotExist()
        compose.onNodeWithText("副翼舵效衰减 50.0%").assertDoesNotExist()
    }

    @Test fun independentThrottleHudPreservesBoostAndClearsMissingEngine() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"throttle 1, %":110,"throttle 2, %":75}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(440.dp, 300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = listOf("engine1_throttle"), hudAttitude = false, hudMechanization = false),
                emptyList(), null) { androidx.compose.material3.Text("HUD") }
        } } }
        compose.onNodeWithText("1 号油门").assertIsDisplayed()
        compose.onNodeWithText("110 %").assertIsDisplayed()
        compose.onNodeWithTag("hud-throttle-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.onNodeWithTag("hud-throttle-bar").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "油门超过 100%"))
        compose.onNodeWithText("发动机 #1").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map { if (it.index == 1) it.copy(throttlePercent = 0.0) else it }) }
        compose.onNodeWithText("0 %").assertIsDisplayed()
        compose.onNodeWithTag("hud-throttle-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map { if (it.index == 1) it.copy(throttlePercent = 55.0) else it }) }
        compose.onNodeWithTag("hud-throttle-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.5f, 0f..1f))
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map { if (it.index == 1) it.copy(throttlePercent = 120.0) else it }) }
        compose.onNodeWithText("120 %").assertIsDisplayed()
        compose.onNodeWithTag("hud-throttle-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.filter { it.index == 2 }) }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithText("75 %").assertDoesNotExist()
        compose.onNodeWithTag("hud-throttle-bar").assertDoesNotExist()
    }

    @Test fun mechanicalReadingsRejectOutOfRangePercentagesWithoutClamping() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"gear, %":0,"flaps, %":100,"airbrake, %":50}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme { MechanizationPanel(telemetry, null) } }
        compose.onNodeWithText("起落架 0.0%   襟翼 100.0%   减速板 50.0%").assertIsDisplayed()
        for (invalid in listOf(-1.0, 100.1, 65535.0, -65535.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            compose.runOnIdle { telemetry = telemetry.copy(gearPercent = invalid, flapsPercent = invalid, airbrakePercent = invalid) }
            compose.onNodeWithText("起落架 —%   襟翼 —%   减速板 —%").assertIsDisplayed()
        }
        compose.runOnIdle { telemetry = telemetry.copy(gearPercent = 100.0, flapsPercent = 0.0, airbrakePercent = 0.0) }
        compose.onNodeWithText("起落架 100.0%   襟翼 0.0%   减速板 0.0%").assertIsDisplayed()
    }

    @Test fun mechanicalReadingsWarnOnlyForVisibleDeployedComponents() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"gear, %":100,"flaps, %":20}""",
            """{"valid":true}""")!!)
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false))
        var alerts by mutableStateOf(listOf(FlightAlert.GEAR_LIMIT, FlightAlert.FLAP_LIMIT))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 500.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, alerts, null) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        fun warning(text: String) = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, text)
        compose.onAllNodes(warning("${FlightAlert.GEAR_LIMIT.label}；${FlightAlert.FLAP_LIMIT.label}")).assertCountEquals(1)
        compose.runOnIdle { settings = settings.copy(hudGear = false) }
        compose.onAllNodes(warning(FlightAlert.FLAP_LIMIT.label)).assertCountEquals(1)
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = null) }
        compose.onAllNodes(warning(FlightAlert.FLAP_LIMIT.label)).assertCountEquals(0)
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 0.0) }
        compose.onAllNodes(warning(FlightAlert.FLAP_LIMIT.label)).assertCountEquals(0)
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 20.0); alerts = emptyList() }
        compose.onAllNodes(warning(FlightAlert.FLAP_LIMIT.label)).assertCountEquals(0)
    }

    @Test fun aoaThresholdTypingPreservesDecimalDraftWithoutInsertingDigits() {
        var value by mutableStateOf(25.0)
        compose.setContent { MaterialTheme { AoaThresholdSetting("阈值", "typing", value) { value = it } } }
        val input = compose.onNodeWithTag("typing-input")
        input.performTextClearance()
        for ((character, expected) in listOf("3" to "3", "." to "3.", "1" to "3.1", "2" to "3.12")) {
            input.performTextInput(character)
            input.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString(expected)))
        }
        compose.runOnIdle { assertEquals(3.12, value) }
        input.performTextReplacement("invalid")
        compose.onNodeWithTag("typing-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        input.assertTextContains("4.0")
    }

    @Test fun aoaThresholdInputsKeepFractionalValuesAndRejectInvalidEdits() {
        var settings by mutableStateOf(AppSettings(hudAoaWarningPercent = 20.25, hudAoaBarWarningPercent = 25.5))
        compose.setContent { MaterialTheme { Column(Modifier.requiredWidth(500.dp)) {
            AoaThresholdSetting("迎角数值预警阈值", "value", settings.hudAoaWarningPercent) {
                settings = settings.copy(hudAoaWarningPercent = it)
            }
            AoaThresholdSetting("正迎角余量条预警阈值", "bar", settings.hudAoaBarWarningPercent) {
                settings = settings.copy(hudAoaBarWarningPercent = it)
            }
        } } }
        compose.onNodeWithTag("value-input").assertTextContains("20.25")
        compose.onNodeWithTag("value-input").performTextReplacement("31.125")
        compose.runOnIdle { assertEquals(31.125, settings.hudAoaWarningPercent); assertEquals(25.5, settings.hudAoaBarWarningPercent) }
        for (bad in listOf("", "NaN", "Infinity", "-1", "100.01", "abc")) {
            compose.onNodeWithTag("value-input").performTextReplacement(bad)
            compose.runOnIdle { assertEquals(31.125, settings.hudAoaWarningPercent) }
            compose.onNodeWithText("请输入 0–100 的有限数值；仍使用上次有效值。").assertExists()
        }
        compose.onNodeWithTag("bar-input").performTextReplacement("0.25")
        compose.runOnIdle { assertEquals(0.25, settings.hudAoaBarWarningPercent) }
        compose.onNodeWithTag("value-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(40f) }
        compose.onNodeWithTag("value-input").assertTextContains("40.0")
        compose.runOnIdle { settings = AppSettings() }
        compose.onNodeWithTag("value-input").assertTextContains("20.0")
        compose.onNodeWithTag("bar-input").assertTextContains("25.0")
    }

    @Test fun speedReadingsFollowTheirOwnAlertsAndClearOnMissingValues() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":500,"M":0.8}""",
            """{"valid":true}""")!!)
        var alerts by mutableStateOf(listOf(FlightAlert.IAS_LIMIT))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = listOf("ias", "mach", "tas"), hudAttitude = false, hudMechanization = false),
                alerts, null) { androidx.compose.material3.Text("HUD") }
        } } }
        fun warning(alert: FlightAlert) = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, alert.label)
        compose.onNodeWithText("500 km/h").assert(warning(FlightAlert.IAS_LIMIT))
        compose.onAllNodes(warning(FlightAlert.MACH_LIMIT)).assertCountEquals(0)
        compose.runOnIdle { alerts = listOf(FlightAlert.MACH_LIMIT) }
        compose.onAllNodes(warning(FlightAlert.IAS_LIMIT)).assertCountEquals(0)
        compose.onNodeWithText("0.80").assert(warning(FlightAlert.MACH_LIMIT))
        compose.runOnIdle { telemetry = telemetry.copy(mach = null) }
        compose.onAllNodes(warning(FlightAlert.MACH_LIMIT)).assertCountEquals(0)
        compose.runOnIdle { telemetry = telemetry.copy(mach = 0.9); alerts = emptyList() }
        compose.onAllNodes(warning(FlightAlert.MACH_LIMIT)).assertCountEquals(0)
    }

    @Test fun aoaMarginBarUpdatesAndClearsForMissingOrStaleModel() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"AoA, deg":5,"flaps, %":0}""",
            """{"valid":true,"type":"test"}""")!!)
        val wing = voidmei.fm.WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)
        val model = AircraftAlertModel("test", voidmei.fm.FlightModelParameters(null, null, listOf(wing), false, emptyList()))
        var settings by mutableStateOf(AppSettings(hudFields = listOf("aoa"), hudAttitude = false, hudMechanization = false))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                settings, emptyList(), model) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithText("距模型正迎角限 15.0°").assertIsDisplayed()
        compose.onNodeWithTag("aoa-margin-bar").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 100.0) }
        compose.onNodeWithText("距模型正迎角限 5.0°").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudAoaBarWarningPercent = 50.0) }
        compose.onNodeWithText("正迎角余量低于阈值").assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudAoaBarWarningPercent = 51.0) }
        compose.onNodeWithText("正迎角余量低于阈值").assertIsDisplayed()
        val warning = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "模型迎角余量预警")
        compose.onAllNodes(warning).assertCountEquals(0)
        compose.runOnIdle { settings = settings.copy(hudAoaWarningPercent = 50.0) }
        compose.onAllNodes(warning).assertCountEquals(0)
        compose.runOnIdle { settings = settings.copy(hudAoaWarningPercent = 50.5, hudAoaBarWarningPercent = 0.0) }
        compose.onAllNodes(warning).assertCountEquals(1)
        compose.onNodeWithText("正迎角余量低于阈值").assertDoesNotExist()

        compose.runOnIdle { settings = settings.copy(hudAoaBarWarningPercent = 0.0) }
        compose.onNodeWithText("正迎角余量低于阈值").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(angleOfAttackDeg = 12.0) }
        compose.onNodeWithText("距模型正迎角限 -2.0°").assertIsDisplayed()
        compose.onNodeWithText("已达模型正迎角限").assertIsDisplayed()
        compose.onNodeWithTag("aoa-margin-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle { telemetry = telemetry.copy(angleOfAttackDeg = -5.0) }
        compose.onNodeWithText("距模型正迎角限 15.0°").assertIsDisplayed()
        compose.onNodeWithTag("aoa-margin-bar").assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 0f..1f))
        compose.runOnIdle { settings = settings.copy(hudFields = emptyList()) }
        compose.onNodeWithTag("aoa-margin-bar").assertDoesNotExist()
        compose.onNodeWithText("距模型正迎角限", substring = true).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFields = listOf("aoa")) }
        compose.onNodeWithTag("aoa-margin-bar").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "other") }
        compose.onNodeWithText("距模型正迎角限 —").assertIsDisplayed()
        compose.onAllNodes(warning).assertCountEquals(0)
        compose.onNodeWithTag("aoa-margin-bar").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "test", angleOfAttackDeg = null) }
        compose.onNodeWithTag("aoa-margin-bar").assertDoesNotExist()
    }

    @Test fun hudMechanizationSwitchesToSweepOnlyForKnownRetractedVariableWing() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"flaps, %":20}""",
            """{"valid":true,"type":"test","wing_sweep_indicator":0.5}""")!!)
        val parameters = voidmei.fm.FlightModelParameters(null, null, emptyList(), true, emptyList())
        var model by mutableStateOf<AircraftAlertModel?>(AircraftAlertModel("test", parameters))
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false,
            hudGear = false, hudAirbrake = false))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), model) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithText("襟翼 20.0%").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 0.0) }
        compose.onNodeWithText("后掠 50.0%").assertIsDisplayed()
        compose.onNodeWithText("当前 IAS 下表内最大襟翼", substring = true).assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = 0.0) }
        compose.onNodeWithText("后掠 0.0%").assertIsDisplayed()
        for (missing in listOf(null, Double.NaN, -1.0, 1.1)) {
            compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = missing) }
            compose.onNodeWithText("后掠 —%").assertIsDisplayed()
        }
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = null, wingSweepRatio = 0.5) }
        compose.onNodeWithText("襟翼 —%").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 0.0, aircraft = "other") }
        compose.onNodeWithText("襟翼 0.0%").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "test"); model = AircraftAlertModel("test", parameters.copy(variableSweep = false)) }
        compose.onNodeWithText("襟翼 0.0%").assertIsDisplayed()
        compose.runOnIdle { model = AircraftAlertModel("test", parameters); settings = settings.copy(hudFlaps = false) }
        compose.onNodeWithText("后掠", substring = true).assertDoesNotExist()
    }

    @Test fun hudMechanizationChoicesHideReadingsAndFlapModelHintsIndependently() {
        val telemetry = TelemetryParser.parse("""{"valid":true,"gear, %":100,"flaps, %":20,"airbrake, %":0}""",
            """{"valid":true}""")!!
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudAttitude = false))
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), null) {
                androidx.compose.material3.Text("HUD")
            }
        } } }
        compose.onNodeWithText("起落架 100.0%   襟翼 20.0%   减速板 0.0%").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudFlaps = false) }
        compose.onNodeWithText("起落架 100.0%   减速板 0.0%").assertIsDisplayed()
        compose.onNodeWithText("当前 IAS 下表内最大襟翼", substring = true).assertDoesNotExist()
        compose.onNodeWithText("当前襟翼开度的模型限速", substring = true).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudGear = false) }
        compose.onNodeWithText("减速板 0.0%").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(hudAirbrake = false) }
        compose.onNodeWithText("减速板", substring = true).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudFlaps = true, hudMechanization = false) }
        compose.onNodeWithText("襟翼", substring = true).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudMechanization = true) }
        compose.onNodeWithText("襟翼 20.0%").assertIsDisplayed()
        compose.onNodeWithText("当前 IAS 下表内最大襟翼", substring = true).assertIsDisplayed()
    }

    @Test fun hudControlSurfaceReadingsUpdateAndClearWithoutKeepingOldDeflection() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"aileron, %":-75.5,"elevator, %":25,"rudder, %":0}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.requiredSize(440.dp, 300.dp)) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = listOf("aileron", "elevator", "rudder"), hudAttitude = false, hudMechanization = false),
                emptyList(), null) { androidx.compose.material3.Text("HUD") }
        } } }
        for (label in listOf("副翼", "升降舵", "方向舵")) compose.onNodeWithText(label).assertIsDisplayed()
        for (value in listOf("-75.5 %", "25.0 %", "0.0 %")) compose.onNodeWithText(value).assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aileronPercent = 50.0, elevatorPercent = null, rudderPercent = -20.0) }
        compose.onNodeWithText("50.0 %").assertIsDisplayed()
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithText("-20.0 %").assertIsDisplayed()
        compose.onNodeWithText("-75.5 %").assertDoesNotExist()
        compose.onNodeWithText("25.0 %").assertDoesNotExist()
    }

    @Test fun gameMessagesDisplayTextAndPreserveHistoryOnFailure() {
        var state by mutableStateOf(HudMessageState(listOf(HudMessage(HudMessageKind.DAMAGE, 7, "发动机 \"过热\""))))
        compose.setContent { MaterialTheme { Column { HudMessageList(state) } } }
        compose.onNodeWithText("损伤 #7 · 发动机 \"过热\"").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(error = "离线") }
        compose.onNodeWithText("消息更新失败：离线（保留已有记录）").assertIsDisplayed()
        compose.onNodeWithText("损伤 #7 · 发动机 \"过热\"").assertIsDisplayed()
        compose.runOnIdle { state = HudMessageState(emptyList()) }
        compose.onNodeWithText("尚无游戏消息").assertIsDisplayed()
        compose.onNodeWithText("损伤 #7 · 发动机 \"过热\"").assertDoesNotExist()
    }

    @Test fun mapClickCapturesDistanceWithoutFollowingReorderedObjects() {
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(2000.0, 1000.0), 1, null, null)
        var snapshot by mutableStateOf(MapSnapshot(bounds, MapTelemetryParser.objects("""[
            {"icon":"Player","x":0,"y":0.25},
            {"type":"aircraft","icon":"target","x":0.5,"y":0.25}]""")))
        compose.setContent { MaterialTheme { Column { MapObjectPlot(snapshot) } } }
        compose.onNodeWithTag("map-objects-plot").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height * 3 / 8f)) }
        compose.onNodeWithText("点击时对象：aircraft · target").assertIsDisplayed()
        compose.onNodeWithText("点击时到玩家的平面距离：1000 m").assertIsDisplayed()
        compose.runOnIdle { snapshot = snapshot.copy(objects = snapshot.objects.reversed().map { it.copy(position = MapPoint(0.9, 0.9)) }) }
        compose.onNodeWithText("点击时到玩家的平面距离：1000 m").assertIsDisplayed()
        compose.runOnIdle { snapshot = snapshot.copy(bounds = bounds.copy(generation = 2)) }
        compose.onNodeWithText("点击时对象：aircraft · target").assertDoesNotExist()
        compose.onNodeWithText("清除对象选择").assertDoesNotExist()
    }

    @Test fun mapScaleUpdatesDistanceWithoutChangingItsPixelMeaning() {
        var width by mutableStateOf(2000.0)
        compose.setContent { MaterialTheme { Column {
            MapObjectPlot(MapSnapshot(MapBounds(MapPoint(0.0, 0.0), MapPoint(width, 1000.0), 1, null, null), emptyList()))
        } } }
        compose.onNodeWithText("距离标尺：500 m").assertIsDisplayed()
        fun barLength(): Int {
            val pixels = compose.onNodeWithTag("map-distance-scale").captureToImage().toPixelMap()
            val y = pixels.height / 2
            return (0 until pixels.width).count { x -> val c = pixels[x, y]; c.red > 0.9f && c.green > 0.9f && c.blue > 0.9f }
        }
        val first = barLength()
        assertTrue(first > 0)
        compose.runOnIdle { width = 20000.0 }
        compose.onNodeWithText("距离标尺：5 km").assertIsDisplayed()
        assertEquals(first, barLength())
    }

    @Test fun wideMapBackgroundAndPlayerShareCenteredAspectRatio() {
        val raster = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0..1) for (x in 0..1) raster.setRGB(x, y, 0x2266CC)
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(2000.0, 1000.0), 1, null, null)
        val objects = MapTelemetryParser.objects("""[{"icon":"Player","x":0.5,"y":0.25}]""")
        compose.setContent { MaterialTheme { Column { MapObjectPlot(MapSnapshot(bounds, objects), raster.toComposeImageBitmap()) } } }
        val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
        val outside = pixels[pixels.width / 8, pixels.height / 8]
        val inside = pixels[pixels.width / 8, pixels.height * 7 / 16]
        assertTrue(inside.blue > 0.7f && inside.red < 0.2f)
        assertFalse(outside.blue > 0.7f && outside.red < 0.2f)
        val player = pixels[pixels.width / 2, pixels.height * 3 / 8]
        assertTrue(player.red > 0.9f && player.green > 0.9f && player.blue < 0.2f)
    }

    @Test fun mapBackgroundRendersBelowObjectsAndCanBeWithdrawn() {
        val raster = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (y in 0..1) for (x in 0..1) raster.setRGB(x, y, 0x2266CC)
        var background by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(raster.toComposeImageBitmap())
        val bounds = MapBounds(MapPoint(0.0, 0.0), MapPoint(1000.0, 1000.0), 1, null, null)
        val objects = MapTelemetryParser.objects("""[{"icon":"Player","x":0.5,"y":0.5}]""")
        compose.setContent { MaterialTheme { Column { MapObjectPlot(MapSnapshot(bounds, objects), background) } } }
        compose.onNodeWithContentDescription("地图底图与对象位置方向").assertIsDisplayed()
        val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
        val base = pixels[pixels.width / 8, pixels.height / 8]
        assertTrue(base.blue > 0.7f && base.red < 0.2f)
        val player = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(player.red > 0.9f && player.green > 0.9f && player.blue < 0.2f)
        compose.runOnIdle { background = null }
        compose.onNodeWithContentDescription("地图对象位置与方向示意，不含底图").assertIsDisplayed()
    }

    @Test fun hudNewWarningsStayVisibleWhileFlightDataIsScrolledToBottom() {
        var alerts by mutableStateOf<List<FlightAlert>>(emptyList())
        val telemetry = TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(320f))) {
                HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                    AppSettings(hudFields = HudField.entries.map { it.id }), alerts, null) {
                    androidx.compose.material3.Text("拖动 HUD")
                }
            }
        } }
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo()
        compose.runOnIdle { alerts = FlightAlert.entries.filter { it.severity == AlertSeverity.ADVISORY } }
        compose.onNodeWithText("提示 · ${FlightAlert.LOW_FUEL.label}").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { alerts = alerts + FlightAlert.IAS_LIMIT }
        compose.onNodeWithText("警告 · ${FlightAlert.IAS_LIMIT.label}").assertIsDisplayed()
        compose.onNodeWithText("拖动 HUD").assertIsDisplayed()
        val before = compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot()
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot())
        compose.onNodeWithText("警告 · ${FlightAlert.IAS_LIMIT.label}").assertIsDisplayed()
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithTag("flight-alerts").assertDoesNotExist()
    }

    @Test fun hudScrollSurvivesSamplesButResetsForAnotherAircraftAndFieldOrder() {
        var flying by mutableStateOf(true)
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"IAS, km/h":321}""",
            """{"valid":true,"type":"first","aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!)
        var fields by mutableStateOf(HudField.entries.map { it.id })
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(220f))) {
                HudPanel(if (flying) ConnectionState.Flying(telemetry, FlightMetrics()) else ConnectionState.WaitingForFlight,
                    AppSettings(hudFields = fields), emptyList(), null) {
                    androidx.compose.material3.Text("拖动 HUD")
                }
            }
        } }
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(iasKmh = 322.0, aircraft = "FIRST") }
        compose.onNodeWithText("俯仰 0.0°", substring = true).assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "second") }
        compose.onNodeWithText("322 km/h").assertIsDisplayed()
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo()
        compose.runOnIdle { fields = listOf("fuel", "ias") + fields.filterNot { it in listOf("fuel", "ias") } }
        compose.onNodeWithText("322 km/h").assertIsDisplayed()
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo()
        compose.runOnIdle { flying = false }
        compose.onNodeWithText("已连接 · 等待飞行").assertIsDisplayed()
        compose.runOnIdle { flying = true }
        compose.onNodeWithText("322 km/h").assertIsDisplayed()
    }

    @Test fun headingCanDisplayWithAttitudeDisabledAndClearsOnMissingCompass() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"compass":359.9}""")!!)
        compose.setContent { MaterialTheme {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                AppSettings(hudFields = listOf("heading"), hudAttitude = false, hudMechanization = false), emptyList(), null) {}
        } }
        compose.onNodeWithText("航向").assertIsDisplayed()
        compose.onNodeWithText("0 °").assertIsDisplayed()
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
        compose.runOnIdle { telemetry = telemetry.copy(headingDeg = -90.0) }
        compose.onNodeWithText("270 °").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(headingDeg = null) }
        compose.onNodeWithText("— °").assertIsDisplayed()
    }

    @Test fun hudHeaderStaysVisibleWhenLongContentIsScrolled() {
        var closed = false
        val flight = ConnectionState.Flying(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!, FlightMetrics())
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(220f))) {
                HudPanel(flight, AppSettings(hudFields = HudField.entries.map { it.id }), emptyList(), null) {
                    androidx.compose.material3.TextButton(onClick = { closed = true }) { androidx.compose.material3.Text("关闭 HUD") }
                }
            }
        } }
        val before = compose.onNodeWithTag("hud-header").getUnclippedBoundsInRoot()
        compose.onNodeWithText("俯仰 0.0°", substring = true).performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithTag("hud-header").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        compose.onNodeWithText("关闭 HUD").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun hudMeasuredHeightFitsModelLegendsAndFontScalingThenShrinksOnDisconnect() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("Vne:r=800")).copy(
            wings = listOf(voidmei.fm.WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)))
        val model = AircraftAlertModel("test", parameters)
        var state by mutableStateOf<ConnectionState>(ConnectionState.Flying(TelemetryParser.parse(
            """{"valid":true,"IAS, km/h":321,"flaps, %":0,"AoA, deg":5,"AoS, deg":2}""",
            """{"valid":true,"type":"test","aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!, FlightMetrics()))
        var height by mutableStateOf(androidx.compose.ui.unit.Dp(120f))
        var fontScale by mutableStateOf(1f)
        var hudFontScale by mutableStateOf(1f)
        var measurements = 0
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, fontScale)) {
                MaterialTheme { Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), height)) {
                    HudPanel(state, AppSettings(hudFontScale = hudFontScale), emptyList(), model, onContentHeightChanged = {
                        measurements++
                        height = it.coerceIn(androidx.compose.ui.unit.Dp(120f), androidx.compose.ui.unit.Dp(900f))
                    }) { androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("关闭") } }
                } }
            }
        }
        compose.waitUntil(5000) { height.value > 120f }
        fun assertBottomVisible() {
            val panel = compose.onNodeWithTag("hud-panel").getUnclippedBoundsInRoot()
            val bottom = compose.onNodeWithText("红虚线：模型迎角限", substring = true).getUnclippedBoundsInRoot()
            assertTrue(bottom.bottom <= panel.bottom, "$bottom outside $panel")
            compose.onNodeWithText("321 km/h").assertIsDisplayed()
        }
        assertBottomVisible()
        val normalHeight = height
        compose.runOnIdle { fontScale = 1.25f }
        compose.waitUntil(5000) { height > normalHeight }
        assertBottomVisible()
        val systemScaledHeight = height
        compose.runOnIdle { hudFontScale = 1.2f }
        compose.waitUntil(5000) { height > systemScaledHeight }
        assertBottomVisible()
        compose.runOnIdle { state = ConnectionState.Disconnected("test") }
        compose.waitUntil(5000) { height < normalHeight }
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
        compose.runOnIdle { assertTrue(measurements < 16, "Unexpected layout feedback: $measurements measurements") }
    }

    @Test fun lateralHudFieldsShowSignedReadingsAndClearOnMissingData() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"AoS, deg":-3.2,"Wx, deg/s":-45.5}""", """{"valid":true}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics()), compact = true,
                fields = listOf(HudField.SIDESLIP, HudField.ROLL_RATE), mechanization = false)
        } }
        compose.onNodeWithText("侧滑角").assertIsDisplayed()
        compose.onNodeWithText("滚转角速度").assertIsDisplayed()
        compose.onNodeWithText("-3.2 °").assertIsDisplayed()
        compose.onNodeWithText("-45.5 °/s").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(sideslipAngleDeg = null, rollRateDegPerSecond = null) }
        compose.onNodeWithText("— °").assertIsDisplayed()
        compose.onNodeWithText("— °/s").assertIsDisplayed()
    }

    @Test fun automaticManifoldHudWaitsSwitchesUnitsAndResetsOnAircraftChange() {
        val calculator = FlightCalculator()
        val base = TelemetryParser.parse("""{"valid":true,"manifold pressure 1, atm":0.5}""",
            """{"valid":true,"type":"metric"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(base, FlightMetrics()))
        fun feed(aircraft: String, scale: Double, start: Long) {
            for (i in 0..10) {
                val t = base.copy(aircraft = aircraft, altitudeM = 1000.0 + i, altimeterRaw = 3000 + i * scale)
                flight = ConnectionState.Flying(t, calculator.update(t, start + i * 1000L))
            }
        }
        compose.setContent { MaterialTheme {
            FlightPanel(flight, compact = true, fields = listOf(HudField.ENGINE1_MANIFOLD_AUTO), mechanization = false)
        } }
        compose.onNodeWithText("— 待判定").assertIsDisplayed()
        compose.runOnIdle { feed("metric", 1.0, 0) }
        compose.onNodeWithText("0.50 atm").assertIsDisplayed()
        compose.runOnIdle { feed("imperial", 3.28084, 11000) }
        compose.onNodeWithText("-7.3 psi · 15.0 inHg").assertIsDisplayed()
        compose.runOnIdle {
            val t = base.copy(aircraft = "another", altitudeM = 1011.0, altimeterRaw = 3036.0)
            flight = ConnectionState.Flying(t, calculator.update(t, 22000))
        }
        compose.onNodeWithText("— 待判定").assertIsDisplayed()
    }

    @Test fun wepHudShowsUpperBoundsAndClearsTimeWhenDisabled() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("""
            Mass { MaxNitro:r=10 }
            Engine0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.5 } }
        """))
        val model = AircraftAlertModel("test", parameters)
        val t = TelemetryParser.parse("""{"valid":true,"throttle 1, %":110}""",
            """{"valid":true,"type":"test"}""")!!
        val monitor = WepFuelMonitor()
        var flight by mutableStateOf(monitor.update(ConnectionState.Flying(t, FlightMetrics()), model, 0) as ConnectionState.Flying)
        compose.setContent { MaterialTheme {
            FlightPanel(flight, compact = true, fields = listOf(HudField.WEP_FUEL, HudField.WEP_TIME),
                mechanization = false, model = model)
        } }
        compose.onNodeWithText("WEP 燃料上限").assertIsDisplayed()
        compose.onNodeWithText("10.0 kg").assertIsDisplayed()
        compose.onNodeWithText("00:20").assertIsDisplayed()
        compose.runOnIdle { flight = monitor.update(flight, model, 1000) as ConnectionState.Flying }
        compose.onNodeWithText("9.5 kg").assertIsDisplayed()
        compose.onNodeWithText("00:19").assertIsDisplayed()
        compose.runOnIdle { flight = monitor.update(flight, model, 1500) as ConnectionState.Flying }
        // 18.5 seconds is an upper bound: displaying 18 would understate it.
        compose.onNodeWithText("00:19").assertIsDisplayed()
        compose.runOnIdle {
            val off = t.copy(engines = t.engines.map { it.copy(throttlePercent = 100.0) })
            flight = monitor.update(ConnectionState.Flying(off, FlightMetrics()), model, 2000) as ConnectionState.Flying
        }
        compose.onNodeWithText("9.0 kg").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.runOnIdle { flight = monitor.update(flight, null, 3000) as ConnectionState.Flying }
        compose.onNodeWithText("— kg").assertIsDisplayed()
    }

    @Test fun boosterFuelHudPreservesZeroAndClearsMissingChannel() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"Mfuel 1, kg":50,"Mfuel0 1, kg":200}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(t, FlightMetrics()), compact = true,
                fields = listOf(HudField.BOOSTER_FUEL, HudField.BOOSTER_FUEL_PERCENT), mechanization = false)
        } }
        compose.onNodeWithText("50.0 kg").assertIsDisplayed()
        compose.onNodeWithText("25 %").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(boosterFuelKg = 0.0) }
        compose.onNodeWithText("0.0 kg").assertIsDisplayed()
        compose.onNodeWithText("0 %").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(boosterFuelKg = null) }
        compose.onNodeWithText("— kg").assertIsDisplayed()
        compose.onNodeWithText("— %").assertIsDisplayed()
    }

    @Test fun engineResponseHudShowsSignedRateAndClearsWhenReferenceChanges() {
        val t = TelemetryParser.parse("""{"valid":true,"power 1, hp":1000}""",
            """{"valid":true,"type":"test"}""")!!
        val monitor = EngineResponseMonitor()
        fun sample(percent: Double, time: Long, reference: Double = 1000.0): ConnectionState.Flying {
            val input = ConnectionState.Flying(t, FlightMetrics(observedEnginePeak = ObservedEnginePeak(
                voidmei.fm.EnginePeakKind.SHAFT_POWER_HP, percent, reference)))
            return monitor.update(input, null, time) as ConnectionState.Flying
        }
        var flight by mutableStateOf(sample(0.0, 0))
        compose.setContent { MaterialTheme {
            FlightPanel(flight, compact = true, fields = listOf(HudField.ENGINE_RESPONSE), mechanization = false)
        } }
        compose.onNodeWithText("— %/s").assertIsDisplayed()
        compose.runOnIdle { flight = sample(10.0, 1000) }
        compose.onNodeWithText("6.3 %/s").assertIsDisplayed()
        compose.runOnIdle { flight = sample(0.0, 2000) }
        compose.onNodeWithText("-4.0 %/s").assertIsDisplayed()
        compose.runOnIdle { flight = sample(0.0, 3000, 2000.0) }
        compose.onNodeWithText("— %/s").assertIsDisplayed()
    }

    @Test fun thermalHudDisplaysBudgetRangeAndRejectsStaleObservation() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("""
            Engine0 { Main { Type:t=Inline }
                Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=10; RecoverTime:r=5 } } }
        """))
        val model = AircraftAlertModel("test", parameters)
        val t = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(t, FlightMetrics()))
        val monitor = EngineThermalMonitor()
        var observation by mutableStateOf(monitor.update(flight, model, 0))
        compose.setContent { MaterialTheme {
            HudPanel(flight, AppSettings(hudFields = listOf("heat_tolerance"), hudAttitude = false,
                hudMechanization = false), emptyList(), model, thermal = observation) { }
        } }
        compose.onNodeWithText("0.0–10.0 s").assertIsDisplayed()
        compose.runOnIdle { observation = monitor.update(flight, model, 2000) }
        compose.onNodeWithText("0.0–8.0 s").assertIsDisplayed()
        compose.runOnIdle { observation = monitor.update(flight, model, 3960) }
        compose.onNodeWithText("0.0–6.1 s").assertIsDisplayed()
        compose.runOnIdle { flight = flight.copy(telemetry = t.copy(aircraft = "other")) }
        compose.onNodeWithText("— s").assertIsDisplayed()
        compose.onNodeWithText("0.0–8.0 s").assertDoesNotExist()
    }

    @Test fun powerPercentHudRetainsReferenceAfterDelayAndClearsAfterUnexplainedGap() {
        val calculator = FlightCalculator()
        val t = TelemetryParser.parse("""{"valid":true,"power 1, hp":1000,"throttle 1, %":100,"magneto 1":3}""",
            """{"valid":true,"type":"test"}""")!!
        var flight by mutableStateOf(ConnectionState.Flying(t, calculator.update(t, 0)))
        compose.setContent { MaterialTheme {
            FlightPanel(flight, compact = true, fields = listOf(HudField.POWER_PERCENT), mechanization = false)
        } }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.runOnIdle {
            for (i in 1..5) flight = ConnectionState.Flying(t, calculator.update(t, i * 1000L))
        }
        compose.onNodeWithText("100 % · 历史全油门峰值").assertIsDisplayed()
        compose.runOnIdle {
            val low = t.copy(engines = t.engines.map { it.copy(powerHp = 500.0, throttlePercent = 50.0) })
            flight = ConnectionState.Flying(low, calculator.update(low, 6000))
        }
        compose.onNodeWithText("50 % · 历史全油门峰值").assertIsDisplayed()
        compose.runOnIdle {
            calculator.pause()
            val low = t.copy(engines = t.engines.map { it.copy(powerHp = 250.0, throttlePercent = 50.0) })
            flight = ConnectionState.Flying(low, calculator.update(low, 9000))
        }
        compose.onNodeWithText("25 % · 历史全油门峰值").assertIsDisplayed()
        compose.runOnIdle { flight = ConnectionState.Flying(t, calculator.update(t, 12000)) }
        compose.onNodeWithText("— %").assertIsDisplayed()
    }

    @Test fun powerPercentHudClearsOnAircraftChangeAndShowsZero() {
        val doc = voidmei.fm.BlkParser.parse("""
            Engine0 { Main { Type:t=Jet; AfterburnerBoost:r=2 }
                ThrustMax { ThrustMax0:r=1000; Altitude_0:r=0; Velocity_0:r=0; ThrustMaxCoeff_0_0:r=1 } }
        """)
        val parameters = voidmei.fm.FlightModelExtractor.extract(doc).copy(
            enginePeaks = voidmei.fm.EnginePeakExtractor.extract(doc))
        val model = AircraftAlertModel("test", parameters)
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"thrust 1, kgs":1000}""",
            """{"valid":true,"type":"test"}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(t, FlightMetrics()), compact = true,
                fields = listOf(HudField.POWER_PERCENT), mechanization = false, model = model)
        } }
        compose.onNodeWithText("50 % · FM 峰值").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(engines = t.engines.map { it.copy(thrustKgf = 0.0) }) }
        compose.onNodeWithText("0 % · FM 峰值").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(aircraft = "another") }
        compose.onNodeWithText("— %").assertIsDisplayed()
    }

    @Test fun propulsiveEfficiencyHudDistinguishesZeroFromMissingShaftPower() {
        var t by mutableStateOf(TelemetryParser.parse("""{"valid":true,"TAS, km/h":360,
            "power 1, hp":1500,"thrust 1, kgs":700}""", """{"valid":true}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(t, FlightCalculator().update(t, 0)), compact = true,
                fields = listOf(HudField.PROPULSIVE_EFFICIENCY), mechanization = false)
        } }
        compose.onNodeWithText("62.3 %").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(tasKmh = 0.0) }
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
        compose.runOnIdle { t = t.copy(engines = t.engines.map { it.copy(powerHp = 0.0) }) }
        compose.onNodeWithText("— %").assertIsDisplayed()
    }

    @Test fun manifoldHudDistinguishesAbsolutePressureAndSignedBoost() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"manifold pressure 1, atm":0.5}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics()), compact = true,
                fields = listOf(HudField.ENGINE1_MANIFOLD_ATM, HudField.ENGINE1_MANIFOLD_INHG,
                    HudField.ENGINE1_BOOST_PSI), mechanization = false)
        } }
        compose.onNodeWithText("0.50 atm").assertIsDisplayed()
        compose.onNodeWithText("15.0 inHg").assertIsDisplayed()
        compose.onNodeWithText("-7.3 psi").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(engines = telemetry.engines.map { it.copy(manifoldPressureAtm = 1.0) }) }
        compose.onNodeWithText("1.00 atm").assertIsDisplayed()
        compose.onNodeWithText("29.9 inHg").assertIsDisplayed()
        compose.onNodeWithText("0.0 psi").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(engines = emptyList()) }
        compose.onNodeWithText("— atm").assertIsDisplayed()
        compose.onNodeWithText("— inHg").assertIsDisplayed()
        compose.onNodeWithText("— psi").assertIsDisplayed()
    }

    @Test fun temperatureHudChangesSourceLabelsWithFallbackAndClearsMissingValues() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"water temp 1, C":90}""",
            """{"valid":true,"water_temperature":100,"head_temperature":200}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics()), compact = true,
                fields = listOf(HudField.ENGINE_TEMPERATURE), mechanization = false)
        } }
        compose.onNodeWithText("100.0 水温仪表原值").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(waterTemperatureRaw = null) }
        compose.onNodeWithText("200.0 缸温仪表原值").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(headTemperatureRaw = null) }
        compose.onNodeWithText("90.0 °C · 1号").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(engines = emptyList()) }
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithText("90.0 °C · 1号").assertDoesNotExist()
    }

    @Test fun massEstimateHudClearsWhenFuelOrMatchingModelIsMissing() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"Mfuel, kg":400}""",
            """{"valid":true,"type":"test-plane"}""")!!)
        val model = AircraftAlertModel("test-plane", voidmei.fm.FlightModelExtractor.extract(
            voidmei.fm.BlkParser.parse("Mass { EmptyMass:r=3000; OilMass:r=25; MaxNitro:r=75 }")))
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics()), compact = true,
                fields = listOf(HudField.MASS_ESTIMATE), mechanization = false, model = model)
        } }
        compose.onNodeWithText("质量估计").assertIsDisplayed()
        compose.onNodeWithText("3500 kg").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 0.0) }
        compose.onNodeWithText("3100 kg").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = null) }
        compose.onNodeWithText("— kg").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 400.0, aircraft = "other-plane") }
        compose.onNodeWithText("— kg").assertIsDisplayed()
        compose.onNodeWithText("3500 kg").assertDoesNotExist()
    }

    @Test fun enduranceClockShowsWarmupEstimateExhaustionAndMissingData() {
        val base = TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
        fun estimated(startKg: Double, duration: Int): ConnectionState.Flying {
            val calculator = FlightCalculator()
            var result = ConnectionState.Flying(base, FlightMetrics())
            for (second in 0..duration) {
                val telemetry = base.copy(fuelKg = startKg - second)
                result = ConnectionState.Flying(telemetry, calculator.update(telemetry, second * 1000L))
            }
            return result
        }
        var flight by mutableStateOf(estimated(100.0, 9))
        compose.setContent { MaterialTheme {
            FlightPanel(flight, compact = true, fields = listOf(HudField.ENDURANCE_CLOCK), mechanization = false)
        } }
        compose.onNodeWithText("续航时间（分:秒）").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.runOnIdle { flight = estimated(100.0, 10) }
        compose.onNodeWithText("01:30").assertIsDisplayed()
        compose.runOnIdle { flight = estimated(10.0, 10) }
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.runOnIdle { flight = ConnectionState.Flying(base, FlightCalculator().update(base, 0)) }
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertDoesNotExist()
    }

    @Test fun thrustPowerHudShowsKilowattsAndClearsMissingReadings() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"TAS, km/h":360,"thrust 1, kgs":200,"thrust 2, kgs":500}""",
            """{"valid":true}""")!!)
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightCalculator().update(telemetry, 0)), compact = true,
                fields = listOf(HudField.THRUST_POWER), mechanization = false)
        } }
        compose.onNodeWithText("推进功率").assertIsDisplayed()
        compose.onNodeWithText("686.5 kW").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(tasKmh = 0.0) }
        compose.onNodeWithText("0.0 kW").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(tasKmh = null) }
        compose.onNodeWithText("— kW").assertIsDisplayed()
        compose.onNodeWithText("686.5 kW").assertDoesNotExist()
    }

    @Test fun sweepHudDistinguishesUnsweptAndUnknownValues() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true}""", """{"valid":true}""")!!.copy(wingSweepRatio = 0.375))
        compose.setContent { MaterialTheme {
            FlightPanel(ConnectionState.Flying(telemetry, FlightMetrics()), compact = true,
                fields = listOf(HudField.WING_SWEEP), mechanization = false)
        } }
        compose.onNodeWithText("后掠").assertIsDisplayed()
        compose.onNodeWithText("37.5 %").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = 0.0) }
        compose.onNodeWithText("0.0 %").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(wingSweepRatio = null) }
        compose.onNodeWithText("— %").assertIsDisplayed()
        compose.onNodeWithText("37.5 %").assertDoesNotExist()
    }

    @Test fun liveThermalBudgetDistinguishesUnknownCoolingAndExhaustion() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("""
            Engine0 { Main { Type:t=Inline }
                Temperature { Load1 { WaterTemperature:r=100; WorkTime:r=4; RecoverTime:r=2 } }
            }
        """))
        val model = AircraftAlertModel("test", parameters)
        val hot = TelemetryParser.parse("""{"valid":true,"water temp 1, C":110}""",
            """{"valid":true,"type":"test"}""")!!
        val monitor = EngineThermalMonitor()
        var state by mutableStateOf<ConnectionState>(ConnectionState.Flying(hot, FlightMetrics()))
        var selectedModel by mutableStateOf<AircraftAlertModel?>(model)
        var observation by mutableStateOf(monitor.update(state, model, 0))
        compose.setContent { MaterialTheme { Column {
            EngineThermalBudgetPanel(observation?.budgetsFor(state, selectedModel).orEmpty())
        } } }
        compose.onNodeWithText("#1 · WaterTemperature 0.0–4.0 s · OilTemperature 未知").assertIsDisplayed()
        compose.onNodeWithText("不是实际剩余寿命", substring = true).assertIsDisplayed()
        compose.runOnIdle { observation = monitor.update(state, model, 2000) }
        compose.onNodeWithText("WaterTemperature 0.0–2.0 s", substring = true).assertIsDisplayed()
        compose.runOnIdle { observation = monitor.update(state, model, 4000) }
        compose.onNodeWithText("WaterTemperature 0.0–0.0 s", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            state = ConnectionState.Flying(hot.copy(engines = hot.engines.map { it.copy(waterTemperatureC = 90.0) }), FlightMetrics())
            observation = monitor.update(state, model, 6000)
        }
        compose.onNodeWithText("WaterTemperature 当前无计时档位", substring = true).assertIsDisplayed()
        compose.runOnIdle { selectedModel = null }
        compose.onNodeWithText("温度工作预算估算").assertDoesNotExist()
        compose.runOnIdle { selectedModel = model }
        compose.onNodeWithText("温度工作预算估算").assertIsDisplayed()
        compose.runOnIdle {
            state = ConnectionState.Disconnected("test")
            observation = monitor.update(state, model, 6100)
        }
        compose.onNodeWithText("温度工作预算估算").assertDoesNotExist()
        compose.runOnIdle {
            state = ConnectionState.Flying(hot, FlightMetrics())
            observation = monitor.update(state, model, 6200)
        }
        compose.onNodeWithText("WaterTemperature 0.0–4.0 s", substring = true).assertIsDisplayed()
    }

    @Test fun engineThermalTableDistinguishesModelBudgetsFromRemainingTime() {
        val model = voidmei.fm.EngineThermalParameters(2, listOf(
            voidmei.fm.EngineThermalBand(0, 80.0, 60.0, null, null),
            voidmei.fm.EngineThermalBand(1, 90.0, 70.0, 7200.0, 3600.0)))
        compose.setContent { MaterialTheme { Column { EngineThermalPanel(listOf(model)) } } }
        compose.onNodeWithText("发动机温度模型").performClick()
        compose.onNodeWithText("发动机 #2 温度档位").assertIsDisplayed()
        compose.onNodeWithText("模型工作时长 未知 s · 恢复时长 未知 s").assertIsDisplayed()
        compose.onNodeWithText("模型工作时长 7200.0 s · 恢复时长 3600.0 s").assertIsDisplayed()
        compose.onNodeWithText("不是剩余寿命", substring = true).assertIsDisplayed()
        compose.onNodeWithText("发动机温度模型").performClick()
        compose.onNodeWithText("发动机 #2 温度档位").assertDoesNotExist()
    }

    @Test fun hudAlertListKeepsReadingsVisibleAndDistinguishesSeverity() {
        var alerts by mutableStateOf(FlightAlert.entries.reversed())
        val telemetry = TelemetryParser.parse("""{"valid":true,"IAS, km/h":321}""", """{"valid":true}""")!!
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(320f))) {
                HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                    AppSettings(hudFields = listOf("ias"), hudAttitude = false, hudMechanization = false), alerts, null) {}
            }
        } }
        compose.onNodeWithText("321 km/h").assertIsDisplayed()
        compose.onNodeWithTag("flight-alert-scrollbar").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("flight-alerts").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= androidx.compose.ui.unit.Dp(HUD_ALERT_HEIGHT_DP.toFloat()))
        compose.onNodeWithText("警告 · ${FlightAlert.HIGH_DESCENT.label}").assertIsDisplayed()
        compose.onNodeWithText("提示 · ${FlightAlert.HIGH_AOA.label}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("321 km/h").assertIsDisplayed()
        compose.runOnIdle { alerts = listOf(FlightAlert.HIGH_AOA, FlightAlert.IAS_LIMIT, FlightAlert.IAS_LIMIT) }
        compose.onAllNodesWithText("警告 · ${FlightAlert.IAS_LIMIT.label}").assertCountEquals(1)
        val warning = compose.onNodeWithTag("flight-alert-IAS_LIMIT").getUnclippedBoundsInRoot()
        val advisory = compose.onNodeWithTag("flight-alert-HIGH_AOA").getUnclippedBoundsInRoot()
        assertTrue(warning.top < advisory.top)
        compose.runOnIdle { alerts = emptyList() }
        compose.onNodeWithTag("flight-alerts").assertDoesNotExist()
        compose.onNodeWithText("321 km/h").assertIsDisplayed()
    }

    @Test fun selectedHudStallEstimateUpdatesAndDoesNotSurviveModelWithdrawal() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "test", iasKmh = 321.0, fuelKg = 0.0, flapsPercent = 0.0))
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("Vne:r=800"))
            .copy(stallSpeed = voidmei.fm.StallSpeedModel(1000.0, listOf(voidmei.fm.StallLiftProfile(0.0, 16.0, 32.0))))
        var model by mutableStateOf<AircraftAlertModel?>(AircraftAlertModel("test", parameters))
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(200f))) {
                HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()),
                    AppSettings(hudFields = listOf("ias", "stall_ias"), hudAttitude = false, hudMechanization = false), emptyList(), model) {}
            }
        } }
        compose.onNodeWithText("1 G 失速IAS").assertIsDisplayed()
        compose.onNodeWithText("114 km/h").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(fuelKg = 1000.0) }
        compose.onNodeWithText("161 km/h").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "other") }
        compose.onNodeWithText("— km/h").assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "test", flapsPercent = 100.0) }
        compose.onNodeWithText("114 km/h").assertIsDisplayed()
        compose.runOnIdle { model = null }
        compose.onNodeWithText("— km/h").assertIsDisplayed()
    }

    @Test fun hudShowsDefaultFlightReadingsWithoutScrollingPastSettings() {
        var connection by mutableStateOf<ConnectionState>(ConnectionState.Flying(
            TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
                .copy(aircraft = "test", iasKmh = 321.0, pitchDeg = 0.0, rollDeg = 0.0), FlightMetrics()))
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(androidx.compose.ui.unit.Dp(440f), androidx.compose.ui.unit.Dp(520f))) {
                HudPanel(connection, AppSettings(), emptyList(), null) {
                    androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("关闭") }
                }
            }
        } }
        compose.onNodeWithText("321 km/h").assertIsDisplayed()
        compose.onNodeWithText("当前襟翼开度的模型限速 — km/h").assertIsDisplayed()
        compose.onNodeWithTag("attitude-canvas").assertIsDisplayed()
        val panel = compose.onNodeWithTag("hud-panel").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithText("俯仰 0.0° · 横滚 0.0°", substring = true).getUnclippedBoundsInRoot()
        assertTrue(bottom.bottom <= panel.bottom && bottom.bottom > bottom.top, "$bottom outside $panel")
        compose.onNodeWithText("语音音量", substring = true).assertDoesNotExist()
        compose.onNodeWithText("语音包", substring = true).assertDoesNotExist()
        compose.runOnIdle { connection = ConnectionState.Disconnected("test") }
        compose.onNodeWithText("连接中断 · 自动重试 · test").assertIsDisplayed()
        compose.onNodeWithText("321 km/h").assertDoesNotExist()
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
    }


    @Test fun mechanizationUsesCurrentAircraftModelAndWithdrawsUnknownLimits() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
            .copy(aircraft = "TEST", iasKmh = 400.0, flapsPercent = 75.0))
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse(
            "FlapsDestructionIndSpeedP:p4=0.5,500,1,300"))
        var model by mutableStateOf<AircraftAlertModel?>(AircraftAlertModel("test", parameters))
        compose.setContent { MaterialTheme { MechanizationPanel(telemetry, model) } }
        compose.onNodeWithText("当前 IAS 下表内最大襟翼 75.0%").assertExists()
        compose.onNodeWithText("当前襟翼开度的模型限速 400.0 km/h").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "other") }
        compose.onNodeWithText("当前 IAS 下表内最大襟翼 —%").assertExists()
        compose.onNodeWithText("当前襟翼开度的模型限速 — km/h").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(aircraft = "test", iasKmh = 600.0, flapsPercent = 25.0) }
        compose.onNodeWithText("当前 IAS 下表内最大襟翼 —%").assertExists()
        compose.onNodeWithText("当前襟翼开度的模型限速 — km/h").assertExists()
        compose.runOnIdle { telemetry = telemetry.copy(iasKmh = 200.0, flapsPercent = 100.0) }
        compose.onNodeWithText("当前 IAS 下表内最大襟翼 100.0%").assertExists()
        compose.onNodeWithText("当前襟翼开度的模型限速 300.0 km/h").assertExists()
        compose.runOnIdle { model = null }
        compose.onNodeWithText("当前 IAS 下表内最大襟翼 —%").assertExists()
        compose.onNodeWithText("当前襟翼开度的模型限速 — km/h").assertExists()
    }


    @Test fun replayPauseAndSpeedChangesPreservePositionInsideLongSampleGap() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,100\n1,11000,10000,test,200\n"
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingReplayPanel(csv, listOf("ias_kmh" to "IAS"))
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-frame").fetchSemanticsNodes().isNotEmpty() }
        fun position(): Double = compose.onNodeWithTag("replay-playhead").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text].single().text.removePrefix("播放位置 ").removeSuffix(" s").toDouble()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("播放回看").performClick()
            compose.mainClock.advanceTimeBy(2000)
            compose.onNodeWithText("暂停回看").performClick()
            compose.mainClock.advanceTimeByFrame()
            val paused = position()
            assertTrue(paused in 1.8..2.1, "$paused")
            compose.mainClock.advanceTimeBy(2000)
            assertEquals(paused, position())
            compose.onNodeWithText("播放回看").performClick()
            compose.mainClock.advanceTimeBy(1000)
            val resumed = position()
            assertTrue(resumed > paused + .8 && resumed < paused + 1.2, "$paused -> $resumed")
            compose.onNodeWithText("2.0×").performClick()
            compose.mainClock.advanceTimeByFrame()
            val speedStart = position()
            assertTrue(speedStart >= resumed, "$resumed -> $speedStart")
            val speedClock = compose.mainClock.currentTime
            compose.mainClock.advanceTimeBy(500)
            val accelerated = position()
            val expectedAdvance = (compose.mainClock.currentTime - speedClock) / 1000.0 * 2
            assertTrue(kotlin.math.abs(accelerated - speedStart - expectedAdvance) < .1,
                "$speedStart -> $accelerated; expected advance $expectedAdvance")
            compose.onNodeWithText("IAS：100.0000").assertExists()
            compose.onNodeWithText("下一原始帧").performClick()
            compose.mainClock.advanceTimeByFrame()
            assertEquals(10.0, position())
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun replayCanLoopSelectedIntervalAndStopAtItsEnd() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,100\n1,2000,1000,test,200\n2,3000,2000,test,300\n"
        var range by mutableStateOf(1000L..2000L)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingReplayPanel(csv, listOf("ias_kmh" to "IAS"), window = range)
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-frame").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("仅回看曲线区间").performClick()
        compose.onNodeWithText("上一原始帧").assertIsNotEnabled()
        compose.onNodeWithText("IAS：200.0000").assertExists()
        compose.onNodeWithContentDescription("循环回看").performClick()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("播放回看").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("暂停回看").assertExists()
        compose.onNodeWithContentDescription("循环回看").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { compose.onAllNodesWithText("暂停回看").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("IAS：300.0000").assertExists()
        compose.runOnIdle { range = 1000L..1000L }
        compose.onNodeWithText("播放回看").assertIsNotEnabled()
        compose.onNodeWithContentDescription("循环回看").assertIsNotEnabled()
    }

    @Test fun retainedPointsSeekExactRawSamplesWithDuplicateTimes() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,300\n1,2000,1000,test,500\n2,2000,1000,test,600\n"
        val full = voidmei.recording.FlightRecordPlots.analyze(csv)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingWindowPanel(csv, full, listOf("ias_kmh" to "IAS"))
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("recording-replay-value").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一个采样点").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("同步原始帧 1 · 1.000 s · 500.0000 · IAS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一个采样点").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("同步原始帧 2 · 1.000 s · 600.0000 · IAS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("上一个采样点").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("同步原始帧 1 · 1.000 s · 500.0000 · IAS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("区间起点 (s)").performScrollTo().performTextReplacement("1")
        compose.onNodeWithText("分析此区间").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("当前曲线：1.0–1.0 s · 2 帧").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一个采样点").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("同步原始帧 2 · 1.000 s · 600.0000 · IAS").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun replayCursorUsesRawFrameValuesAcrossFieldsAndClearsWhenCollapsed() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,fuel_kg\n0,1000,0,test,100,50\n1,2000,1000,test,,40\n2,3000,2000,test,300,30\n"
        val full = voidmei.recording.FlightRecordPlots.analyze(csv)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingWindowPanel(csv, full, listOf("ias_kmh" to "IAS", "fuel_kg" to "燃油"))
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-frame").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一原始帧").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("同步原始帧 1 · 1.000 s · 未知 · IAS").fetchSemanticsNodes().isNotEmpty() }
        val pixels = compose.onNodeWithTag("recording-plot").performScrollTo().captureToImage().toPixelMap()
        val cursor = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(cursor.blue > .9f && cursor.red < .6f && cursor.green > .6f)
        compose.onNodeWithText("燃油", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("同步原始帧 1 · 1.000 s · 40.0000 · 燃油").assertExists()
        compose.onNodeWithText("区间终点 (s)").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("分析此区间").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("（当前曲线区间外）", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("原始帧同步回看").performScrollTo().performClick()
        compose.onNodeWithTag("recording-replay-value").assertDoesNotExist()
    }

    @Test fun rawReplayShowsSynchronizedFieldsAndPlaysToEnd() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,fuel_kg\n0,2000,0,test,100,50\n1,1000,1000,test,,40\n"
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingReplayPanel(csv, listOf("ias_kmh" to "IAS", "fuel_kg" to "燃油"))
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-frame").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("IAS：100.0000").assertExists()
        compose.onNodeWithText("下一原始帧").performClick()
        compose.onNodeWithText("IAS：未知").assertExists()
        compose.onNodeWithText("燃油：40.0000").assertExists()
        compose.onNodeWithText("上一原始帧").performClick()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("播放回看").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { compose.onAllNodesWithText("暂停回看").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("整段原始记录 · 第 2/2 帧 · 采样 1 · 1.000 s").assertExists()
        compose.onNodeWithText("IAS：未知").assertExists()
    }

    @Test fun horizontalDragSelectsOrderedAbsoluteTimesInBothDirections() {
        val analysis = voidmei.recording.FlightRecordPlots.analyze(
            "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,100\n1,2000,1000,test,200\n")
        val selections = mutableListOf<Pair<Long, Long>>()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingPlotPanel(analysis.summary, analysis.plots, timeOffsetMs = 5000) { first, last -> selections += first to last }
        } } }
        compose.onNodeWithTag("recording-plot").performScrollTo().performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .2f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .8f, height * .5f), durationMillis = 400)
        }
        compose.onNodeWithTag("recording-plot").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .8f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .2f, height * .5f), durationMillis = 400)
        }
        compose.runOnIdle {
            assertEquals(2, selections.size)
            selections.forEach { (first, last) ->
                assertTrue(first in 5100..5400 && last in 5600..5900, "$first..$last")
            }
        }
    }

    @Test fun draggingWindowRunsOriginalDataAnalysis() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n" +
            (0..100).joinToString("\n") { "$it,${1000 + it},${it * 100},test,$it" }
        val full = voidmei.recording.FlightRecordPlots.analyze(csv)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RecordingWindowPanel(csv, full) } } }
        compose.onNodeWithTag("recording-plot").performScrollTo().performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .2f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .8f, height * .5f), durationMillis = 400)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("当前曲线：0.0–10.0 s · 101 帧").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("区间分析失败：", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("recording-plot").assertExists()
        compose.onNodeWithText("恢复完整记录").performScrollTo().performClick()
        compose.onNodeWithText("当前曲线：0.0–10.0 s · 101 帧").assertExists()
    }

    @Test fun historicalWindowRecalculatesValuesAndRestoresFullRecord() {
        val csv = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,100\n1,2000,1000,test,200\n2,3000,2000,test,300\n"
        val full = voidmei.recording.FlightRecordPlots.analyze(csv)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RecordingWindowPanel(csv, full) } } }
        compose.onNodeWithText("区间起点 (s)").performTextReplacement("1")
        compose.onNodeWithText("区间终点 (s)").performTextReplacement("1")
        compose.onNodeWithText("分析此区间").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("当前曲线：1.0–1.0 s · 1 帧").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保留采样点 1/1 · 1.000 s · 200.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithText("区间终点 (s)").performTextReplacement("invalid")
        compose.onNodeWithText("分析此区间").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("区间分析失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("当前曲线：1.0–1.0 s · 1 帧").assertExists()
        compose.onNodeWithText("恢复完整记录").performClick()
        compose.onNodeWithText("当前曲线：0.0–2.0 s · 3 帧").assertExists()
    }

    @Test fun historicalPointInspectorHandlesDuplicateTimesAndMissingFields() {
        val analysis = voidmei.recording.FlightRecordPlots.analyze(
            "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,300\n1,2000,1000,test,500\n2,2000,1000,test,600\n")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingPlotPanel(analysis.summary, analysis.plots)
        } } }
        compose.onNodeWithText("保留采样点 1/3 · 0.000 s · 300.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithTag("recording-point-slider").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithText("保留采样点 2/3 · 1.000 s · 500.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithText("下一个采样点").performScrollTo().performClick().assertIsNotEnabled()
        compose.onNodeWithText("保留采样点 3/3 · 1.000 s · 600.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithText("上一个采样点").performClick()
        compose.onNodeWithText("保留采样点 2/3 · 1.000 s · 500.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithTag("recording-plot").performScrollTo().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(1f, height / 2f))
        }
        compose.onNodeWithText("保留采样点 1/3 · 0.000 s · 300.0000 · IAS (km/h)").assertExists()
        compose.onNodeWithText("TAS (km/h)").performScrollTo().performClick()
        compose.onNodeWithTag("recording-inspected-point").assertDoesNotExist()
        compose.onNodeWithTag("recording-point-slider").assertDoesNotExist()
    }

    @Test fun engineTimelineUsesJoinedFlightTimeAndClearsMismatchedReload() {
        val file = Files.createTempFile("engine-timeline-gui", ".csv")
        val engines = "sample_id,utc_epoch_ms,engine_index,rpm\n0,2000,2,1000\n1,1000,2,2000\n"
        try {
            Files.writeString(file, "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,2000,0,test,300\n1,1000,100,test,300\n")
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                EngineTimelinePanel(engines, 2, listOf("rpm" to "转速 (RPM)"), initialPath = file.toString(), chooseFile = { null })
            } } }
            compose.onNodeWithText("对应飞行 CSV 路径（用于发动机曲线）").assertTextContains(file.toString())
            compose.onNodeWithText("关联并绘制发动机曲线").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("recording-plot").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("2000.00 · 转速 (RPM)").assertExists()
            compose.onNodeWithText("选择对应飞行文件").performScrollTo().performClick()
            compose.onNodeWithText("2000.00 · 转速 (RPM)").assertExists()
            compose.onNodeWithText("1000.00 · 0–0.1 s").assertExists()
            compose.onNodeWithText("区间起点 (s)").performScrollTo().performTextReplacement("0.1")
            compose.onNodeWithText("区间终点 (s)").performTextReplacement("0.1")
            compose.onNodeWithText("分析此区间").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("当前曲线：0.1–0.1 s · 1 帧").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("保留采样点 1/1 · 0.100 s · 2000.0000 · 转速 (RPM)").assertExists()
            compose.onNodeWithText("恢复完整记录").performClick()
            compose.onNodeWithText("当前曲线：0.0–0.1 s · 2 帧").assertExists()
            Files.writeString(file, Files.readString(file).replace("1,1000,100", "1,1001,100"))
            compose.onNodeWithText("关联并绘制发动机曲线").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("关联失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("recording-plot").assertDoesNotExist()
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun engineRecordSummarySwitchesEngineAndClearsFailedReload() {
        val file = Files.createTempFile("engine-record-gui", ".csv")
        try {
            Files.writeString(file, "sample_id,utc_epoch_ms,engine_index,rpm\n0,1000,1,1000\n0,1000,2,2000\n")
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { EngineRecordingPanel() } } }
            compose.onNodeWithText("发动机记录分析").performClick()
            compose.onNodeWithText("发动机 CSV 文件路径").performTextInput(file.toString())
            compose.onNodeWithText("读取发动机摘要").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("发动机 #1 · 1 条记录", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("转速 (RPM)：1000.00–1000.00 · 有效 1/1 条").assertExists()
            compose.onNodeWithText("选择发动机 #1").performScrollTo().performClick()
            compose.onNodeWithText("发动机 #2").performClick()
            compose.onNodeWithText("转速 (RPM)：2000.00–2000.00 · 有效 1/1 条").assertExists()
            Files.writeString(file, "broken")
            compose.onNodeWithText("读取发动机摘要").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("发动机记录读取失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("选择发动机 #2").assertDoesNotExist()
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun additionalHistoricalFieldsCanBeSelectedFromMenu() {
        val analysis = voidmei.recording.FlightRecordPlots.analyze(
            "sample_id,utc_epoch_ms,elapsed_ms,aircraft,gear_percent\n0,1000,0,test,0\n1,2000,1000,test,100\n")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingPlotPanel(analysis.summary, analysis.plots)
        } } }
        compose.onNodeWithText("IAS (km/h)").performClick()
        compose.onNodeWithTag("recording-plot").assertDoesNotExist()
        compose.onNodeWithText("更多记录字段").performScrollTo().performClick()
        compose.onNodeWithText("起落架 (%)").performScrollTo().performClick()
        compose.onNodeWithText("已选字段：起落架 (%)").assertExists()
        compose.onNodeWithText("100.00 · 起落架 (%)").assertExists()
        compose.onNodeWithTag("recording-plot").performScrollTo().assertExists()
    }

    @Test fun historicalFuelPressureCanBeSelectedAndShowsRawReplayValue() {
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,fuel_pressure_raw\n0,1000,0,test,9.7\n1,2000,1000,test,0\n"
        val analysis = voidmei.recording.FlightRecordPlots.analyze(text)
        val replay = voidmei.recording.RecordedReplay(text)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingPlotPanel(analysis.summary, analysis.plots, replayFrame = replay.frame(1))
        } } }
        compose.onNodeWithText("更多记录字段").performScrollTo().performClick()
        compose.onNodeWithText("燃油压力原值（仪表单位）").performScrollTo().performClick()
        compose.onNodeWithText("已选字段：燃油压力原值（仪表单位）").assertExists()
        compose.onNodeWithText("9.70 · 燃油压力原值（仪表单位）").assertExists()
        compose.onNodeWithTag("recording-replay-value").assertTextEquals(
            "同步原始帧 1 · 1.000 s · 0.0000 · 燃油压力原值（仪表单位）")
        compose.onNodeWithTag("recording-plot").performScrollTo().assertExists()
    }

    @Test fun historicalRadarPlotUsesInstrumentUnitsAndDoesNotRelabelAsMetres() {
        val analysis = voidmei.recording.FlightRecordPlots.analyze(
            "sample_id,utc_epoch_ms,elapsed_ms,aircraft,radio_altitude_raw,vertical_speed_mps\n" +
                "0,1000,0,test,328.084,-2\n1,2000,1000,test,300,-3\n")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RecordingPlotPanel(analysis.summary, analysis.plots)
        } } }
        compose.onNodeWithText("雷达高度原值（仪表单位）").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("328.08 · 雷达高度原值（仪表单位）").assertExists()
        compose.onNodeWithTag("recording-plot").performScrollTo().assertExists()
        compose.onNodeWithText("垂直速度 (m/s)").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("-2.00 · 垂直速度 (m/s)").assertExists()
        compose.onNodeWithText("高度 (m)").performScrollTo().performClick()
        compose.onNodeWithText("所选字段无有效记录").assertExists()
        compose.onNodeWithTag("recording-plot").assertDoesNotExist()
    }

    @Test fun zipVoiceInstallPublishesPackAndRejectsExistingName() {
        val temp = Files.createTempDirectory("voice-zip-gui")
        try {
            val archive = temp.resolve("pack.zip")
            val root = temp.resolve("voice")
            java.util.zip.ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("nested/aoaCrit.wav"))
                javaClass.getResourceAsStream("/voice/aoaCrit.wav")!!.use { it.copyTo(zip) }
                zip.closeEntry()
            }
            var installedCount = 0
            var selectedArchive: String? = null
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                VoicePackInstallPanel(root.toString(), chooseFile = { selectedArchive }) { installedCount++ }
            } } }
            compose.onNodeWithText("校验并安装 ZIP").assertIsNotEnabled()
            compose.onNodeWithText("选择 ZIP 语音包").performClick()
            compose.onNodeWithText("校验并安装 ZIP").assertIsNotEnabled()
            compose.runOnIdle { selectedArchive = archive.toString() }
            compose.onNodeWithText("选择 ZIP 语音包").performClick()
            compose.onNodeWithText("语音 ZIP 路径").assertTextContains(archive.toString())
            assertFalse(Files.exists(root))
            compose.onNodeWithText("校验并安装 ZIP").assertIsNotEnabled()
            compose.onNodeWithText("新语音包目录名").performTextInput("custom")
            compose.onNodeWithText("校验并安装 ZIP").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已安装 1 个 WAV", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(Files.exists(root.resolve("custom/aoaCrit.wav")))
            compose.runOnIdle { assertEquals(1, installedCount) }
            compose.runOnIdle { selectedArchive = null }
            compose.onNodeWithText("选择 ZIP 语音包").performClick()
            compose.onNodeWithText("已安装 1 个 WAV", substring = true).assertExists()
            compose.onNodeWithText("语音 ZIP 路径").assertTextContains(archive.toString())
            compose.onNodeWithText("校验并安装 ZIP").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("安装失败：语音包已存在", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(1, installedCount) }
        } finally { temp.toFile().deleteRecursively() }
    }

    @Test fun stoppingVoicePreviewCancelsPendingLoadAndAllowsRetry() {
        var attempts = 0
        var cancelled = false
        var completed = false
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AlertVoicePanel(AppSettings(voiceVolume = 75), onPreview = {
                attempts++
                if (attempts == 1) {
                    try { kotlinx.coroutines.awaitCancellation() }
                    finally { cancelled = true }
                } else completed = true
            }) {}
        } } }
        compose.onNodeWithText("逐条语音设置").performClick()
        compose.onNodeWithText("试听燃油耗尽").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, attempts) }
        compose.onNodeWithText("试听燃油耗尽").assertIsNotEnabled()
        compose.onNodeWithText("停止试听").performScrollTo().performClick()
        compose.waitUntil(5000) { cancelled }
        compose.onNodeWithText("试听燃油耗尽").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(completed); assertEquals(2, attempts) }
    }

    @Test fun voicePreviewKeepsSettingsAndStopsWhenCollapsed() {
        var current by mutableStateOf(AppSettings(voiceVolume = 75))
        val played = mutableListOf<FlightAlert>()
        var stopped = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AlertVoicePanel(current, onPreview = { played += it }, onStopPreview = { stopped++ }) { current = it }
        } } }
        compose.onNodeWithText("逐条语音设置").performClick()
        compose.onNodeWithText("试听燃油耗尽").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(FlightAlert.EMPTY_FUEL), played)
            assertEquals(AppSettings(voiceVolume = 75), current)
            current = current.copy(voiceVolume = 0)
        }
        compose.onNodeWithText("试听燃油耗尽").assertIsNotEnabled()
        compose.onNodeWithText("停止试听").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, stopped) }
        compose.onNodeWithText("逐条语音设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, stopped) }
    }

    @Test fun perAlertVoiceControlsKeepUnrelatedSettingsAndRejectBadPack() {
        var current by mutableStateOf(AppSettings(voiceVolume = 75))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            AlertVoicePanel(current) { current = it }
        } } }
        compose.onNodeWithText("逐条语音设置").performClick()
        compose.onNodeWithContentDescription("燃油耗尽播报").performClick().assertIsOff()
        compose.onNodeWithText("燃油耗尽语音包").performTextReplacement("custom")
        compose.onNodeWithText("应用燃油耗尽语音包").performClick()
        compose.runOnIdle {
            assertEquals(voidmei.config.VoiceChoice(false, "custom"), current.alertVoices["fail_nofuel"])
            assertEquals(75, current.voiceVolume)
        }
        compose.onNodeWithText("燃油耗尽语音包").performTextReplacement("../escape")
        compose.onNodeWithText("应用燃油耗尽语音包").performClick()
        compose.onNodeWithText("语音包名称必须为单个目录名").assertExists()
        compose.runOnIdle { assertEquals("custom", current.alertVoices["fail_nofuel"]?.pack) }
        compose.onNodeWithText("燃油耗尽语音包").performTextReplacement("")
        compose.onNodeWithText("应用燃油耗尽语音包").performClick()
        compose.runOnIdle { assertNull(current.alertVoices["fail_nofuel"]?.pack) }
        compose.onNodeWithContentDescription("连接成功提示音播报").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(voidmei.config.VoiceChoice(), current.alertVoices["start1"])
            assertEquals(75, current.voiceVolume)
        }
    }

    @Test fun voicePackValidationAppliesSettingsAndRetainsThemAfterFailure() {
        val root = Files.createTempDirectory("voice-pack-gui")
        try {
            var current by mutableStateOf(AppSettings(voiceVolume = 75))
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                VoicePackPanel(current) { current = it }
            } } }
            compose.onNodeWithText("自定义语音包 · default").performClick()
            compose.onNodeWithText("语音根目录").performTextReplacement(root.toString())
            compose.onNodeWithText("语音包目录名").performTextReplacement("custom")
            compose.onNodeWithText("检查并应用语音包").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已应用语音包", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                assertEquals("custom", current.voicePack)
                assertEquals(root.toString(), current.voiceDirectory)
                assertEquals(75, current.voiceVolume)
            }
            compose.onNodeWithText("语音包目录名").performTextReplacement("../escape")
            compose.onNodeWithText("检查并应用语音包").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("语音包读取失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals("custom", current.voicePack) }
            val damaged = Files.createDirectory(root.resolve("truncated"))
            val bytes = javaClass.getResourceAsStream("/voice/start1.wav")!!.use { it.readBytes() }
            Files.write(damaged.resolve("start1.wav"), bytes.copyOf(bytes.size / 2))
            compose.onNodeWithText("语音包目录名").performTextReplacement("truncated")
            compose.onNodeWithText("检查并应用语音包").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("WAV 音频数据不完整", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals("custom", current.voicePack) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun legacyUnsupportedOnlyFileShowsReportAndDisablesApply() {
        val file = Files.createTempFile("voidmei-unsupported-settings", ".cfg")
        val original = """(panel p (item "自定义字体" :target fontChoice :type input :value "old-font"))"""
        Files.writeString(file, original)
        var applied = false
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { file.toString() }) { applied = true }
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("此文件没有可应用的设置。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("未迁移 1 项：查看").performScrollTo().performClick()
            compose.onNodeWithText("自定义字体（fontChoice）").assertExists()
            compose.onNodeWithText("应用预览设置").assertIsNotEnabled()
            compose.runOnIdle { assertFalse(applied) }
            assertEquals(original, Files.readString(file))
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun legacyResourceDirectoryChangeInvalidatesPreview() {
        val root = Files.createTempDirectory("legacy-resource-gui-")
        try {
            val source = root.resolve("old.cfg")
            Files.writeString(source, "(panel p (item x :target dataPollIntervalMs :type input :value 80))")
            compose.setContent { MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { source.toString() }, onApply = {})
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("应用预览设置").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("旧程序资源目录（留空使用配置所在目录）").performScrollTo().performTextInput(root.toString())
            compose.onNodeWithText("应用预览设置").assertDoesNotExist()
            compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("应用预览设置").fetchSemanticsNodes().isNotEmpty() }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun legacyCrosshairPreviewDistinguishesDisableFromSizeOnlyImport() {
        val file = Files.createTempFile("voidmei-crosshair-preview", ".cfg")
        var current = AppSettings(hudCrosshair = true, hudCrosshairImage = "/custom.png")
        try {
            Files.writeString(file, """(panel p (item e :target displayCrosshair :type switch :value false))""")
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { file.toString() }) { current = it.applyTo(current) }
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("准星：关闭；保留当前样式与位置。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(AppSettings(hudCrosshairImage = "/custom.png"), current) }
            Files.writeString(file, """(panel p (item s :target crosshairScale :type slider :value 100))""")
            compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("准星位置：HUD 右侧；保留当前开关。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(AppSettings(hudCrosshairSizeDp = 200, hudCrosshairRight = true), current) }
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun legacySettingsRequirePreviewAndApplyAndClearStalePreview() {
        val file = Files.createTempFile("voidmei-settings-gui", ".cfg")
        val original = """(panel p (item x :target "dataPollIntervalMs" :type slider :value 80)
            (item port :target "httpPort" :type input :value 9222)
            (item focus :target "autoHideOnFocusLoss" :type switch :value true)
            (item logging :target "enableLogging" :type switch :value true)
            (item energy :target "showHUDEnergy" :type switch :value false)
            (item miniHeight :target "showHUDAltitude" :type switch :value false)
            (item height :target "getAltitude" :type data :value true)
            (item speedMode :target "hudMach" :type switch :value true)
            (item textMaster :target "drawHUDtext" :type switch :value false)
            (item machData :target "getMach" :type data :value true)
            (item threshold :target "miniHUDaoaBarWarningRatio" :type slider :value 0.255)
            (item label :target "disableHUDSpeedLabel" :type switch-inv :value false)
            (item reticle :target "displayCrosshair" :type switch :value true)
            (item size :target "crosshairScale" :type slider :value 100)
            (item coordinate :target "attitudeIndicatorInertialMode" :type switch :value true))"""
        Files.writeString(file, original)
        var current = AppSettings(hudOpacity = .4f)
        var selected: String? = file.toString()
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                LegacySettingsPanel(chooseFile = { selected }) { current = it.applyTo(current) }
            } } }
            compose.onNodeWithText("导入旧版设置").performClick()
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("应用预览设置").assertDoesNotExist()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("刷新间隔：80 ms").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(100, current.pollIntervalMs)
            assertEquals("http://127.0.0.1:8111", current.endpoint)
            compose.onNodeWithText("遥测端口：9222；保留已保存的主机地址，点击“连接”后切换当前连接。").assertExists()
            assertFalse(current.hudAutoHideOnFocusLoss)
            assertFalse(current.recordingAutoStart)
            compose.onNodeWithText("HUD 能量高度：隐藏").assertExists()
            compose.onNodeWithText("HUD 高度：显示").assertExists()
            compose.onNodeWithText("正迎角余量条预警阈值：25.5%（旧值 0–1 按比例转换，1 表示 100%）").assertExists()
            compose.onNodeWithText("罗盘坐标系：航向朝上；姿态图：地面参考。").assertExists()
            compose.onNodeWithText("HUD IAS标签：隐藏（保留数值）").assertExists()
            compose.onNodeWithText("HUD Mach标签：隐藏（保留数值）").assertExists()
            assertTrue(current.hudHiddenLabels.isEmpty())
            compose.onNodeWithText("线框准星：开启；新版位于 HUD 右侧。").assertExists()
            compose.onNodeWithText("线框准星跨度：200 dp；旧比例乘以 2，仅换算准星，不改变 HUD 字号或布局。").assertExists()
            assertFalse(current.hudCrosshair)
            compose.onNodeWithText("HUD 姿态：关闭").assertExists()
            compose.onNodeWithText("HUD IAS：隐藏").assertExists()
            compose.onNodeWithText("HUD 燃油质量占比估计：隐藏").assertExists()
            compose.onNodeWithText("HUD 迎角：隐藏").assertExists()
            compose.onNodeWithText("HUD Mach：显示").assertExists()
            compose.onNodeWithText("HUD 起落架：隐藏").assertExists()
            compose.onNodeWithText("HUD 襟翼/后掠及模型提示：隐藏").assertExists()
            compose.onNodeWithText("HUD 减速板：隐藏").assertExists()
            compose.onNodeWithText("下次启动自动开启记录：开启", substring = true).assertExists()
            compose.onNodeWithText("切出游戏时隐藏 HUD：开启", substring = true).assertExists()
            compose.onNodeWithText("应用预览设置").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(AppSettings(endpoint = "http://127.0.0.1:9222", pollIntervalMs = 80, hudOpacity = .4f,
                hudAutoHideOnFocusLoss = true, recordingAutoStart = true,
                hudFields = HudField.defaults.filter { it !in listOf("energy", "ias", "sep", "load", "aoa") },
                hudCrosshair = true, hudCrosshairRight = true, hudCrosshairSizeDp = 200, hudHiddenLabels = listOf("ias", "mach"), hudAttitude = false, hudAttitudeEarthFixed = true, hudCompassHeadingUp = true, hudGear = false, hudFlaps = false, hudFlapBar = false, hudAirbrake = false, hudAoaBarWarningPercent = 25.5), current) }
            assertEquals(original, Files.readString(file))
            compose.onNodeWithText("预览旧设置").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("应用预览设置").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { selected = null }
            compose.onNodeWithText("选择旧版设置文件").performScrollTo().performClick()
            compose.onNodeWithText("应用预览设置").assertExists()
            compose.onNodeWithText("旧版布局文件路径（UTF-8）").assertTextContains(file.toString())
            compose.runOnIdle { selected = file.toString() + ".missing" }
            compose.onNodeWithText("选择旧版设置文件").performClick()
            compose.onNodeWithText("应用预览设置").assertDoesNotExist()
            compose.onNodeWithText("预览旧设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("读取失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("应用预览设置").assertDoesNotExist()
        } finally { Files.deleteIfExists(file) }
    }

    @Test fun mapPlotDrawsPlayerAndAirfieldSegment() {
        val bounds = MapTelemetryParser.info("""{"valid":true,"map_min":[0,0],"map_max":[1000,1000]}""")!!
        val objects = MapTelemetryParser.objects("""[
            {"type":"aircraft","icon":"Player","x":0.25,"y":0.25,"dx":1,"dy":0},
            {"type":"airfield","color":"#0000FF","sx":0.1,"sy":0.5,"ex":0.9,"ey":0.5}
        ]""")
        compose.setContent { MaterialTheme { Column { MapObjectPlot(MapSnapshot(bounds, objects)) } } }
        compose.onNodeWithText("玩家位置 0.250, 0.250").assertExists()
        val pixels = compose.onNodeWithTag("map-objects-plot").captureToImage().toPixelMap()
        val player = pixels[pixels.width / 4, pixels.height / 4]
        assertTrue(player.red > 0.9f && player.green > 0.9f && player.blue < 0.1f)
        val direction = pixels[(pixels.width * 0.29).toInt(), pixels.height / 4]
        assertTrue(direction.red > 0.9f && direction.green > 0.9f && direction.blue < 0.1f)
        val runway = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(runway.blue > 0.9f && runway.red < 0.1f)
    }

    @Test fun legacyFormatAndEncodingControlsImportMinutesWithoutUtc() {
        val file = Files.createTempFile("voidmei-legacy-gui", ".csv")
        fun row(time: String) = MutableList(32) { "0" }.apply { this[0] = time; this[2] = "300"; this[31] = "" }.joinToString(",")
        val text = voidmei.recording.LegacyFlightRecordReader.header + "\n" + row("0") + "\n" + row("0.5")
        Files.write(file, text.toByteArray(java.nio.charset.Charset.forName("GB18030")))
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RecordingAnalysisPanel() } } }
            compose.onNodeWithText("旧版中文 CSV").performClick().assertIsSelected()
            compose.onNodeWithText("此旧格式的时间列按分钟换算。", substring = true).assertExists()
            compose.onNodeWithText("飞行 CSV 文件路径").performTextInput(file.toString())
            compose.onNodeWithText("读取摘要").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("读取失败：", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("GB18030 / GBK", substring = false).performClick().assertIsSelected()
            compose.onNodeWithText("读取摘要").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("未知机型 · 2 帧 · 采样跨度 30.0 s").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("未知 — 未知 · UTC").assertExists()
            compose.onNodeWithText("旧版中文 CSV · GB18030 / GBK · 时间按分钟转换").assertExists()
            compose.onNodeWithText("Kotlin CSV", substring = false).performScrollTo().performClick()
            // Editing the next import settings must not relabel the already loaded result.
            compose.onNodeWithText("旧版中文 CSV · GB18030 / GBK · 时间按分钟转换").assertExists()
        } finally { Files.delete(file) }
    }

    @Test fun recordingSummaryLoadsAndClearsOldResultOnFailure() {
        val file = Files.createTempFile("voidmei-summary-gui", ".csv")
        Files.writeString(file, "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,300\n1,2000,1000,test,450\n")
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RecordingAnalysisPanel() } } }
            compose.onNodeWithText("飞行 CSV 文件路径").performTextInput(file.toString())
            compose.onNodeWithText("读取摘要").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("test · 2 帧", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("IAS (km/h)：300.00–450.00 · 有效 2/2 帧").assertExists()
            val pixels = compose.onNodeWithTag("recording-plot").performScrollTo().captureToImage().toPixelMap()
            var drawn = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.green > 0.7f && c.blue > 0.6f && c.red < 0.65f) drawn++
            }
            assertTrue(drawn > 100)
            compose.onNodeWithText("TAS (km/h)", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("所选字段无有效记录").assertExists()
            compose.onNodeWithTag("recording-plot").assertDoesNotExist()
            compose.onNodeWithText("IAS (km/h)", substring = false).performClick()
            compose.onNodeWithTag("recording-plot").assertExists()
            Files.writeString(file, "broken")
            compose.onNodeWithText("读取摘要").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("不是 Kotlin 飞行记录 CSV", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("test · 2 帧", substring = true).assertDoesNotExist()
        } finally { Files.delete(file) }
    }

    @Test fun jetCurveInspectsInterpolatedHeightAndSpeed() {
        val model = voidmei.fm.JetThrustModel("EngineType0", listOf(0.0, 10000.0), listOf(0.0, 1000.0),
            listOf(listOf(1000.0, 2000.0), listOf(500.0, 1000.0)),
            listOf(listOf(1500.0, 3000.0), listOf(750.0, 1500.0)))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { JetThrustCurvePanel(model) } } }
        compose.onNodeWithText("展开推力曲线").performClick()
        compose.onNodeWithText("速度 0 km/h · 军用 1000 · 加力 1500 kgf/台").assertExists()
        val pixels = compose.onNodeWithTag("jet-curve-plot").performScrollTo().captureToImage().toPixelMap()
        var colored = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val c = pixels[x, y]
            if ((c.green > 0.7f && c.blue > 0.6f && c.red < 0.65f) ||
                (c.red > 0.9f && c.green > 0.6f && c.blue < 0.5f)) colored++
        }
        assertTrue(colored > 200)
        compose.onNodeWithTag("jet-curve-altitude").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithText("查看高度 5000 m").assertExists()
        compose.onNodeWithTag("jet-curve-plot").performScrollTo().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width / 2f, height / 2f))
        }
        compose.onNodeWithText("速度 500 km/h · 军用 1125 · 加力 1688 kgf/台").assertExists()
        compose.onNodeWithTag("jet-curve-speed").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithText("速度 500 km/h · 军用 1125 · 加力 1688 kgf/台").assertExists()
        compose.onNodeWithText("收起推力曲线").performScrollTo().performClick()
        compose.onNodeWithTag("jet-curve-plot").assertDoesNotExist()
        compose.onNodeWithText("展开推力曲线").performScrollTo().performClick()
        compose.onNodeWithText("查看高度 5000 m").assertExists()
        compose.onNodeWithText("速度 500 km/h · 军用 1125 · 加力 1688 kgf/台").assertExists()
    }

    @Test fun fuelSelectionUpdatesPredictionAndResetsAfterReload() {
        val root = Files.createTempDirectory("voidmei-fuel-gui")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("fueltest.blkx"), """
            fmFile:t="fm/fueltest.blk"
            modifications { ussr_fuel_b-100 { effects { addHorsePowers:i=50 } } }
        """.trimIndent())
        Files.writeString(directory.resolve("fm/fueltest.blkx"), """
            EngineType0 {
                Main { Type:t=Inline; Power:r=1200; AfterburnerBoost:r=1.2 }
                Compressor { NumSteps:i=2; Altitude0:r=4000; Power0:r=1400; Altitude1:r=5000; Power1:r=900; ATA0:r=1.3; AfterburnerManifoldPressure:r=1.6 }
                Propeller { ThrottleRPMAuto0:p2=1.0,2400; ThrottleRPMAuto1:p2=1.1,2400 }
            }
            Engine0 { Type:i=0 }
        """.trimIndent())
        val telemetry = TelemetryParser.parse("""{"valid":true,"H, m":0,"TAS, km/h":0}""",
            """{"valid":true,"type":"fueltest"}""")!!
        var loads = 0
        var published: voidmei.fm.FlightModelParameters? = null
        try {
            compose.setContent { MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    FlightModelPanel(telemetry, root.toString(), { aircraft, parameters -> published = parameters; if (aircraft != null) loads++ }, {})
                }
            } }
            compose.waitUntil(10000) { loads == 1 }
            compose.onNodeWithText("不追加修正").assertIsSelected()
            compose.runOnIdle { assertNull(published?.compressorFuel); assertEquals(1200.0, published!!.engineCompressors.getValue(1).military.stages[0].deckPower) }
            compose.onNodeWithText("当前高度/TAS 模型功率：军用 1200.00", substring = true).assertExists()
            compose.onNodeWithText("B-100", substring = false).performScrollTo().performClick()
            compose.waitUntil(5000) { loads == 2 }
            compose.onNodeWithText("B-100", substring = false).assertIsSelected()
            compose.onNodeWithText("当前高度/TAS 模型功率：军用 1221.60", substring = true).assertExists()
            compose.runOnIdle { assertEquals("ussr_fuel_b-100", published?.compressorFuel?.id); assertEquals(1221.6, published!!.engineCompressors.getValue(1).military.stages[0].deckPower, 0.001) }
            compose.onNodeWithText("展开功率曲线").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("power-curve-plot").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("高度–功率 · 单台发动机 · B-100").assertExists()
            compose.onNodeWithText("高度 0 m · 军用 1222 hp", substring = true).assertExists()
            compose.onNodeWithText("重新加载").performScrollTo().performClick()
            compose.waitUntil(10000) { loads == 3 }
            compose.onNodeWithText("不追加修正").assertIsSelected()
            compose.onNodeWithText("当前高度/TAS 模型功率：军用 1200.00", substring = true).assertExists()
            compose.onNodeWithTag("power-curve-plot").assertDoesNotExist()
            compose.runOnIdle { assertNull(published?.compressorFuel) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun powerCurveControlsRenderAndInspectAltitude() {
        val stage = voidmei.fm.CompressorStage(3000.0, 1400.0, 1200.0)
        val model = voidmei.fm.PistonModels(voidmei.fm.PistonMilitaryModel(listOf(stage), 2400.0),
            listOf(stage.copy(wepPowerMult = 1.2)), null)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PowerCurvePanel(model) } } }
        compose.onNodeWithText("展开功率曲线").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("power-curve-plot").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("高度 0 m · 军用 1200 hp · WEP 1440 hp").assertExists()
        val pixels = compose.onNodeWithTag("power-curve-plot").performScrollTo().captureToImage().toPixelMap()
        var militaryPixels = 0
        var wepPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.green > 0.7f && color.blue > 0.6f && color.red < 0.65f) militaryPixels++
            if (color.red > 0.9f && color.green > 0.6f && color.blue < 0.5f) wepPixels++
        }
        assertTrue(militaryPixels > 100)
        assertTrue(wepPixels > 100)
        compose.onNodeWithTag("power-curve-altitude").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(5000f) }
        compose.onNodeWithText("高度 5000 m", substring = true).assertExists()
        compose.onNodeWithText("600 km/h").performScrollTo().performClick().assertIsSelected()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("power-curve-plot").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("30°C").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("收起功率曲线").performScrollTo().performClick()
        compose.onNodeWithTag("power-curve-plot").assertDoesNotExist()
    }

    @Test fun hudFieldControlsChangeSelectionOrderAndRestoreDefaults() {
        var settings by mutableStateOf(AppSettings(hudFields = listOf("ias", "fuel")))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HudSettingsPanel(settings) { settings = it }
                }
            }
        }
        compose.openHudSettingsPage("fields")
        compose.onNodeWithTag("hud-label-ias").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ias"), settings.hudHiddenLabels) }
        compose.onNodeWithTag("hud-up-ias").assertIsNotEnabled()
        compose.onNodeWithTag("hud-up-fuel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("fuel", "ias"), settings.hudFields) }
        compose.onNodeWithTag("hud-toggle-fuel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ias"), settings.hudFields) }
        compose.onNodeWithTag("hud-add-power").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("ias", "power"), settings.hudFields) }
        compose.openHudSettingsPage("instruments")
        compose.onNodeWithText("姿态：机体参考").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settings.hudAttitudeEarthFixed) }
        compose.onNodeWithText("姿态：地面参考").assertExists()
        compose.onNodeWithText("姿态图").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(settings.hudAttitude) }
        compose.openHudSettingsPage("reset")
        compose.onNodeWithText("恢复默认").performClick()
        compose.runOnIdle {
            assertEquals(HudField.defaults, settings.hudFields)
            assertTrue(settings.hudHiddenLabels.isEmpty())
            assertTrue(settings.hudAttitude)
            assertFalse(settings.hudAttitudeEarthFixed)
        }
    }

    @Test fun hudMapGridUpdatesAndClearsOnUnavailableOrAmbiguousPlayer() {
        val bounds = voidmei.telemetry.MapBounds(voidmei.telemetry.MapPoint(-1000.0, -1000.0),
            voidmei.telemetry.MapPoint(1000.0, 1000.0), 1,
            voidmei.telemetry.MapPoint(200.0, 200.0), voidmei.telemetry.MapPoint(-1000.0, 1000.0))
        val snapshot = voidmei.telemetry.MapSnapshot(bounds, voidmei.telemetry.MapTelemetryParser.objects(
            """[{"icon":"Player","x":0.35,"y":0.25}]"""))
        var state by mutableStateOf<voidmei.telemetry.MapConnection>(voidmei.telemetry.MapConnection.Available(snapshot))
        compose.setContent { MaterialTheme { MapGridReading(state) } }
        compose.onNodeWithText("地图格号：C4").assertExists()
        compose.runOnIdle { state = voidmei.telemetry.MapConnection.Unavailable("offline") }
        compose.onNodeWithText("地图格号：C4").assertDoesNotExist()
        compose.onNodeWithText("地图格号：—").assertExists()
        compose.runOnIdle { state = voidmei.telemetry.MapConnection.Available(snapshot.copy(objects = snapshot.objects + snapshot.objects)) }
        compose.onNodeWithText("地图格号：—").assertExists()
        compose.runOnIdle { state = voidmei.telemetry.MapConnection.Available(snapshot.copy(bounds = bounds.copy(
            gridZero = voidmei.telemetry.MapPoint(-1000.0, 1200.0)))) }
        compose.onNodeWithText("地图格号：D4").assertExists()
        compose.runOnIdle { state = voidmei.telemetry.MapConnection.Waiting }
        compose.onNodeWithText("地图格号：—").assertExists()
    }

    @Test fun attitudePartialDataKeepsValidAngleWithoutInventingHorizon() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":10,"aviahorizon_roll":270}""")!!)
        var earthFixed by mutableStateOf(false)
        compose.setContent { MaterialTheme { AttitudePanel(telemetry, earthFixed = earthFixed) } }
        compose.onNodeWithTag("attitude-canvas").assertExists()
        for (mode in listOf(false, true)) {
            compose.runOnIdle { earthFixed = mode; telemetry = telemetry.copy(pitchDeg = null, rollDeg = 270.0) }
            compose.onNodeWithText("俯仰 —° · 横滚 -90.0°").assertExists()
            compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
            compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = 10.0, rollDeg = null) }
            compose.onNodeWithText("俯仰 -10.0° · 横滚 —°").assertExists()
            compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
            compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = 100.0, rollDeg = Double.NaN) }
            compose.onNodeWithText("俯仰 —° · 横滚 —°").assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = 0.0, rollDeg = 0.0) }
            compose.onNodeWithTag("attitude-canvas").assertExists()
            compose.onNodeWithText("姿态数据不可用").assertDoesNotExist()
        }
    }

    @Test fun attitudeEarthFrameFixesHorizonAndMovesAircraft() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":-10,"aviahorizon_roll":0}""")!!)
        var earthFixed by mutableStateOf(false)
        compose.setContent { MaterialTheme { Box(Modifier.requiredWidth(300.dp)) {
            AttitudePanel(telemetry, earthFixed = earthFixed)
        } } }
        fun aircraftBounds(): List<Int> {
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            val points = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val c = pixels[x, y]
                    if (c.red > .95f && c.green > .75f && c.blue in .4f.. .6f) add(x to y)
                }
            }
            assertTrue(points.size > 30)
            return listOf(points.minOf { it.first }, points.maxOf { it.first },
                points.minOf { it.second }, points.maxOf { it.second })
        }
        val body = aircraftBounds()
        compose.runOnIdle { earthFixed = true }
        val earth = aircraftBounds()
        assertTrue(earth[2] < body[2] - 15)
        val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
        val below = pixels[2, pixels.height / 2 + 10]
        assertTrue(below.red > below.blue)
        compose.runOnIdle { telemetry = telemetry.copy(rollDeg = 90.0) }
        val rolled = aircraftBounds()
        assertTrue(rolled[3] - rolled[2] > 3 * (rolled[1] - rolled[0]))
        compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = null) }
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
    }

    @Test fun attitudeActuallyRendersSkyGroundAndClearsOnMissingData() {
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0,"compass":359.9}""")!!)
        compose.setContent { MaterialTheme { AttitudePanel(telemetry) } }
        compose.onNodeWithText("姿态 · 航向 000°").assertExists()
        val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
        val sky = pixels[2, 2]
        val ground = pixels[2, pixels.height - 3]
        assertTrue(sky.blue > sky.red, "Sky must render above the horizon")
        assertTrue(ground.red > ground.blue, "Ground must render below the horizon")
        compose.runOnIdle { telemetry = telemetry.copy(pitchDeg = null) }
        compose.onNodeWithTag("attitude-canvas").assertDoesNotExist()
        compose.onNodeWithText("姿态数据不可用").assertExists()
    }

    @Test fun attitudeAirflowMarkerMovesInBodyCoordinatesAndDisappearsOnMissingAngles() {
        var telemetry by mutableStateOf(TelemetryParser.parse(
            """{"valid":true,"AoA, deg":15,"AoS, deg":7.5}""",
            """{"valid":true,"aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.requiredWidth(androidx.compose.ui.unit.Dp(440f))) {
            AttitudePanel(telemetry, compact = true)
        } } }
        fun markerPixels(): List<Pair<Int, Int>> {
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            return buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red < 0.6f && color.green > 0.75f && color.blue > 0.65f) add(x to y)
                }
            }
        }
        val leftBottom = markerPixels()
        assertTrue(leftBottom.isNotEmpty())
        compose.runOnIdle { telemetry = telemetry.copy(angleOfAttackDeg = -15.0, sideslipAngleDeg = -7.5, rollDeg = 90.0) }
        val rightTop = markerPixels()
        assertTrue(rightTop.isNotEmpty())
        assertTrue(rightTop.map { it.first }.average() > leftBottom.map { it.first }.average())
        assertTrue(rightTop.map { it.second }.average() < leftBottom.map { it.second }.average())
        compose.runOnIdle { telemetry = telemetry.copy(sideslipAngleDeg = 30.0) }
        compose.onNodeWithContentDescription("姿态仪，迎角/侧滑十字超量程").assertIsDisplayed()
        assertTrue(markerPixels().isEmpty()) // Out-of-scale marker is orange.
        compose.runOnIdle { telemetry = telemetry.copy(sideslipAngleDeg = null) }
        compose.onNodeWithContentDescription("姿态仪，迎角/侧滑十字不可用").assertIsDisplayed()
        assertTrue(markerPixels().isEmpty())
    }

    @Test fun attitudeModelLimitLinesMoveWithFlapsAndClearWhenModelIsWithdrawn() {
        val parameters = voidmei.fm.FlightModelExtractor.extract(voidmei.fm.BlkParser.parse("Vne:r=800")).copy(
            wings = listOf(voidmei.fm.WingConfiguration(0.0, null, null, -10.0, 20.0, -5.0, 10.0)))
        var settings by mutableStateOf(AppSettings(hudFields = emptyList(), hudMechanization = false))
        var model by mutableStateOf<AircraftAlertModel?>(AircraftAlertModel("test", parameters))
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true,"flaps, %":0}""",
            """{"valid":true,"type":"test","aviahorizon_pitch":0,"aviahorizon_roll":0}""")!!)
        compose.setContent { MaterialTheme { Box(Modifier.requiredWidth(androidx.compose.ui.unit.Dp(440f))) {
            HudPanel(ConnectionState.Flying(telemetry, FlightMetrics()), settings, emptyList(), model) {}
        } } }
        fun lineRows(): List<Int> {
            val pixels = compose.onNodeWithTag("attitude-canvas").captureToImage().toPixelMap()
            return buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.9f && color.green < 0.5f && color.blue > 0.35f && color.blue < 0.6f) add(y)
                }
            }
        }
        val clean = lineRows()
        assertTrue(clean.isNotEmpty())
        compose.onNodeWithContentDescription("模型迎角限 -10.0, 20.0°", substring = true).assertIsDisplayed()
        compose.runOnIdle { telemetry = telemetry.copy(flapsPercent = 100.0) }
        val extended = lineRows()
        assertTrue(extended.isNotEmpty())
        assertTrue(extended.min() > clean.min())
        assertTrue(extended.max() < clean.max())
        compose.runOnIdle { settings = settings.copy(hudAttitudeAoaLimits = false) }
        assertTrue(lineRows().isEmpty())
        compose.onNodeWithContentDescription("模型迎角限", substring = true).assertDoesNotExist()
        compose.onNodeWithText("红虚线：模型迎角限", substring = true).assertDoesNotExist()
        compose.runOnIdle { settings = settings.copy(hudAttitudeAoaLimits = true) }
        assertTrue(lineRows().isNotEmpty())
        compose.runOnIdle { model = null }
        assertTrue(lineRows().isEmpty())
        compose.runOnIdle { model = AircraftAlertModel("other", parameters) }
        assertTrue(lineRows().isEmpty())
    }

    @Test fun fmPanelLoadsSwitchesAndReloadsWithoutShowingOldAircraftData() {
        val root = Files.createTempDirectory("voidmei-fm-gui")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("first.blkx"), "fmFile:t=\"fm/first.blk\"")
        Files.writeString(directory.resolve("fm/first.blkx"), "Mass { EmptyMass:r=2500 }")
        var telemetry by mutableStateOf(TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"first"}""")!!)
        var loaded by mutableStateOf<String?>(null)
        try {
            compose.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FlightModelPanel(telemetry, root.toString(), onModel = { aircraft, _ -> loaded = aircraft }, onDataRoot = {})
                    }
                }
            }
            compose.waitUntil(10000) { loaded == "first" }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertExists()
            compose.runOnIdle { telemetry = telemetry.copy(aircraft = "second") }
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("未找到 second", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { assertNull(loaded) }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertDoesNotExist()
            Files.writeString(directory.resolve("second.blkx"), "fmFile:t=\"fm/second.blk\"")
            Files.writeString(directory.resolve("fm/second.blkx"), "Mass { EmptyMass:r=3200 }")
            compose.onNodeWithText("重新加载").performScrollTo().performClick()
            compose.waitUntil(10000) { loaded == "second" }
            compose.onNodeWithText("模型空重 3200.00 kg", substring = true).assertExists()
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
