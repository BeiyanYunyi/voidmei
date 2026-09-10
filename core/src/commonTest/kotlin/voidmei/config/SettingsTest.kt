package voidmei.config

import kotlin.test.*

class SettingsTest {
    @Test fun crosshairSizeAndVisibilityPersistWithOptInDefault() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudCrosshair)
        for (size in listOf(24, 160, 400)) {
            val settings = AppSettings(hudCrosshair = true, hudCrosshairSizeDp = size, hudCrosshairImage = "/images/准星.png", hudCrosshairStretch = true, hudCrosshairRight = true)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (size in listOf(0, 23, 401)) assertFails { AppSettings(hudCrosshairSizeDp = size) }
    }

    @Test fun attitudeFramePersistsWithBodyReferenceDefault() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudAttitudeEarthFixed)
        for (mode in listOf(false, true)) {
            val settings = AppSettings(hudAttitudeEarthFixed = mode)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }

    @Test fun compassModePersistsWithNorthUpDefault() {
        assertFalse(SettingsJson.decode("""{"version":1}""").hudCompassHeadingUp)
        for (mode in listOf(false, true)) {
            val settings = AppSettings(hudCompassHeadingUp = mode)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }

    @Test fun aoaNumericThresholdPersistsAndRejectsOutsideRange() {
        assertEquals(20.0, SettingsJson.decode("""{"version":1}""").hudAoaWarningPercent)
        for (threshold in listOf(0.0, 25.5, 100.0)) {
            val settings = AppSettings(hudAoaWarningPercent = threshold)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (threshold in listOf(-1.0, 101.0)) {
            assertFails { AppSettings(hudAoaWarningPercent = threshold) }
            assertFails { SettingsJson.decode("""{"version":1,"hudAoaWarningPercent":$threshold}""") }
        }
    }

    @Test fun aoaWarningThresholdPersistsAndRejectsOutsideRange() {
        assertEquals(25.0, SettingsJson.decode("""{"version":1}""").hudAoaBarWarningPercent)
        for (threshold in listOf(0.0, 25.5, 100.0)) {
            val settings = AppSettings(hudAoaBarWarningPercent = threshold)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (threshold in listOf(-1.0, 101.0)) {
            assertFails { AppSettings(hudAoaBarWarningPercent = threshold) }
            assertFails { SettingsJson.decode("""{"version":1,"hudAoaBarWarningPercent":$threshold}""") }
        }
    }

    @Test fun independentMechanizationPreferencesPersistWithCompatibleDefaults() {
        val old = SettingsJson.decode("""{"version":1,"hudMechanization":false}""")
        assertFalse(old.hudMechanization)
        assertTrue(old.hudGear && old.hudFlaps && old.hudAirbrake)
        for (gear in listOf(false, true)) for (flaps in listOf(false, true)) for (brake in listOf(false, true)) {
            val settings = old.copy(hudGear = gear, hudFlaps = flaps, hudAirbrake = brake)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }

    @Test fun fullyTransparentHudBackgroundPersistsWithoutChangingVisibleContent() {
        val settings = AppSettings(hudOpacity = 0f, hudEnabled = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(AppSettings().hudFields, settings.hudFields)
        for (opacity in listOf(-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY))
            assertFailsWith<IllegalArgumentException> { AppSettings(hudOpacity = opacity) }
    }

    @Test fun deeplyNestedUnknownSettingsAreRejectedBeforeRecursiveParsing() {
        fun document(arrays: Int) = "{\"version\":1,\"future\":" + "[".repeat(arrays) + "0" + "]".repeat(arrays) + "}"
        val supported = document(63)
        assertEquals(AppSettings(), SettingsJson.decode(supported))
        assertEquals(AppSettings(hudEnabled = true), SettingsJson.decode(SettingsJson.encode(AppSettings(hudEnabled = true), supported)))
        for (depth in listOf(64, 10000)) {
            assertFailsWith<IllegalArgumentException> { SettingsJson.decode(document(depth)) }
            assertFailsWith<IllegalArgumentException> { SettingsJson.encode(AppSettings(), document(depth)) }
        }
    }

    @Test fun escapedQuotesAndBracketsInStringsDoNotCountAsNesting() {
        val value = "\\\"" + "[{".repeat(100) + "}]".repeat(100)
        val literal = kotlinx.serialization.json.JsonPrimitive(value).toString()
        val document = "{\"version\":1,\"future\":$literal}"
        assertEquals(AppSettings(), SettingsJson.decode(document))
        assertTrue(SettingsJson.encode(AppSettings(), document).contains(literal))
        assertFails { SettingsJson.decode("{\"version\":1,\"future\":[}") }
    }

    @Test fun optionalHudEngineSelectionPersistsAndRejectsInvalidIndices() {
        assertNull(SettingsJson.decode("""{"version":1}""").hudEngineIndex)
        for (index in listOf(null, 1, 10, Int.MAX_VALUE)) {
            val settings = AppSettings(hudEngineIndex = index)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (value in listOf("0", "-1", "1.5", "2147483648", "true", "\"2\"")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudEngineIndex":$value}""") }
        }
        assertFails { AppSettings(hudEngineIndex = 0) }
    }

    @Test fun hudWidthPersistsAndRejectsInvalidValues() {
        assertEquals(440, SettingsJson.decode("""{"version":1}""").hudWidthDp)
        for (width in listOf(240, 440, 680, 1000)) {
            val settings = AppSettings(hudWidthDp = width)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        for (value in listOf("239", "1001", "-1", "null", "440.5", "\"440\"", "true")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudWidthDp":$value}""") }
        }
        assertFails { AppSettings(hudWidthDp = 0) }
    }

    @Test fun hudFontScalePersistsWithDefaultsAndRejectsInvalidValues() {
        for (scale in listOf(0.75f, 1f, 1.5f, 2f)) {
            val settings = AppSettings(hudFontScale = scale)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(1f, SettingsJson.decode("""{"version":1}""").hudFontScale)
        for (scale in listOf(0f, 0.74f, 2.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFails { AppSettings(hudFontScale = scale) }
        }
        for (value in listOf("0.74", "2.01", "null", "true", "\"1.5\"")) {
            assertFails { SettingsJson.decode("""{"version":1,"hudFontScale":$value}""") }
        }
    }

    @Test fun perAlertVoicesRoundTripWithInheritanceAndFutureKeys() {
        val settings = AppSettings(alertVoices = mapOf(
            "aoaCrit" to VoiceChoice(false, "custom"), "warn_ias" to VoiceChoice(),
            "future_warning" to VoiceChoice(true, "default"),
        ))
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertTrue(SettingsJson.decode("""{"version":1}""").alertVoices.isEmpty())
        assertFails { VoiceChoice(pack = "../escape") }
    }

    @Test fun voiceResourceSettingsRoundTripAndRejectTraversal() {
        val settings = AppSettings(voiceDirectory = "/tmp/自定义语音", voicePack = "中文包")
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        for (name in listOf("..", "../escape", "a/b", "")) assertFails { AppSettings(voicePack = name) }
        assertFails { AppSettings(voiceDirectory = " ") }
    }

    @Test fun volumePersistsAndRejectsOutOfRangeValues() {
        val settings = AppSettings(voiceVolume = 175)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(100, SettingsJson.decode("""{"version":1}""").voiceVolume)
        for (volume in listOf(-1, 201)) {
            assertFails { AppSettings(voiceVolume = volume) }
            assertFails { SettingsJson.decode("""{"version":1,"voiceVolume":$volume}""") }
        }
    }

    @Test fun roundTripPreservesSettingsAndNegativeMonitorCoordinates() {
        val settings = AppSettings("http://localhost:8111", 250, true, 0.4f, WindowPosition(-1800f, 100f), WindowPosition(50f, 200f), hudHotkeyEnabled = true)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
    }

    @Test fun missingOptionalFieldsUseDefaults() {
        assertEquals(AppSettings(), SettingsJson.decode("""{"version":1}"""))
    }

    @Test fun malformedAndUnsupportedDocumentsAreRejected() {
        for (text in listOf("{", "{}", """{"version":2}""", """{"version":1,"hudOpacity":5}""",
            """{"version":1,"pollIntervalMs":0}""", """{"version":1,"hudPosition":{"x":1}}""")) {
            assertFails { SettingsJson.decode(text) }
            assertFails { SettingsJson.encode(AppSettings(), text) }
        }
    }

    @Test fun unknownTopLevelSettingsSurviveUpdates() {
        val original = """{"version":1,"custom":{"preserve":"是"}}"""
        val updated = SettingsJson.encode(AppSettings(hudEnabled = true), original)
        assertTrue(updated.contains("preserve"))
        assertTrue(updated.contains("是"))
        assertTrue(SettingsJson.decode(updated).hudEnabled)
    }

    @Test fun invalidPositionsAndOpacityCannotBeSaved() {
        assertFails { WindowPosition(Float.NaN, 0f) }
        assertFails { AppSettings(hudOpacity = Float.POSITIVE_INFINITY) }
        assertFails { AppSettings(endpoint = " ") }
    }
}
