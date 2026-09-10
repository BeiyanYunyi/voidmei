package voidmei.config

import kotlin.test.*

class LegacySettingsTest {
    @Test fun disablingCrosshairAlonePreservesImageAndPosition() {
        val imported = LegacySettingsReader.read("""(panel p (item e :target displayCrosshair :type switch :value false))""")
        assertFalse(imported.movesCrosshairRight)
        for (right in listOf(false, true)) {
            val current = AppSettings(hudCrosshair = true, hudCrosshairImage = "/custom.png", hudCrosshairRight = right)
            assertEquals(current.copy(hudCrosshair = false), imported.applyTo(current))
        }
        val sized = LegacySettingsReader.read("""(panel p (item s :target crosshairScale :type slider :value 100))""")
        assertTrue(sized.movesCrosshairRight)
        val current = AppSettings(hudCrosshair = false)
        assertEquals(current.copy(hudCrosshairSizeDp = 200, hudCrosshairRight = true), sized.applyTo(current))
    }

    @Test fun vectorCrosshairImportPreservesCustomTexturesAndUnsupportedSizes() {
        fun read(name: String, enabled: Boolean, size: Int) = LegacySettingsReader.read("""(panel p
            (item n :target crosshairName :type combo :value "$name")
            (item e :target displayCrosshair :type switch :value $enabled)
            (item s :target crosshairScale :type slider :value $size))""")
        val current = AppSettings(hudCrosshair = false, hudCrosshairSizeDp = 80)
        val vector = read("软件渲染准星", true, 100)
        assertEquals(current.copy(hudCrosshair = true, hudCrosshairSizeDp = 200, hudCrosshairRight = true), vector.applyTo(current))
        assertEquals(vector.applyTo(current), SettingsJson.decode(SettingsJson.encode(vector.applyTo(current))))
        val custom = read("custom.png", true, 100)
        assertEquals(current, custom.applyTo(current))
        assertFalse(custom.hasChanges)
        assertEquals(setOf("crosshairName", "displayCrosshair", "crosshairScale"), custom.unmigrated.map { it.target }.toSet())
        assertFalse(read("custom.png", false, 100).applyTo(current.copy(hudCrosshair = true)).hudCrosshair)
        for (size in listOf(0, 1, 11)) {
            val small = read("软件渲染准星", true, size)
            assertEquals(80, small.applyTo(current).hudCrosshairSizeDp)
            assertTrue(small.unmigrated.any { it.target == "crosshairScale" })
        }
        for (size in listOf(12, 200)) assertEquals(size * 2, read("", true, size).hudCrosshairSizeDp)
        assertFails { read("", true, 201) }
    }

    @Test fun invertedLegacyLabelDisplayValuesMapToDisabledFlags() {
        for (speed in listOf(false, true)) for (height in listOf(false, true)) for (sep in listOf(false, true)) {
            val imported = LegacySettingsReader.read("""(panel p
                (item s :target disableHUDSpeedLabel :type switch-inv :value $speed)
                (item h :target disableHUDHeightLabel :type switch-inv :value $height)
                (item e :target disableHUDSEPLabel :type switch-inv :value $sep))""")
            val current = AppSettings(hudHiddenLabels = listOf("future", "ias", "altitude", "sep"))
            val result = imported.applyTo(current)
            assertEquals(mapOf("ias" to !speed, "mach" to !speed, "altitude" to !height, "sep" to !sep), imported.hiddenLabelChoices)
            assertEquals(setOf("future") + imported.hiddenLabelChoices.filterValues { it }.keys, result.hudHiddenLabels.toSet())
            assertEquals(current.hudFields, result.hudFields)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        }
    }

    @Test fun labelSwitchesPreserveReadingsAndUnspecifiedPreferences() {
        for (disabled in listOf(false, true)) {
            val imported = LegacySettingsReader.read("(panel p (item a :target disableHUDSpeedLabel :type switch :value $disabled))")
            val current = AppSettings(hudHiddenLabels = listOf("future", "altitude", "ias"))
            val result = imported.applyTo(current)
            assertEquals(current.hudFields, result.hudFields)
            assertEquals(if (disabled) listOf("future", "altitude", "ias", "mach") else listOf("future", "altitude"), result.hudHiddenLabels)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
            assertTrue(imported.hasChanges)
        }
        assertTrue(SettingsJson.decode("""{"version":1}""").hudHiddenLabels.isEmpty())
    }

    @Test fun automaticGameStartMapsTrayPreferenceIndependentlyOfRecording() {
        assertFalse(SettingsJson.decode("""{"version":1}""").startInTray)
        for (enabled in listOf(false, true)) {
            val imported = LegacySettingsReader.read("(panel p (item a :target autoStartGameMode :type switch :value $enabled))")
            assertTrue(imported.hasChanges)
            val current = AppSettings(recordingAutoStart = true)
            val result = imported.applyTo(current)
            assertEquals(current.copy(startInTray = enabled), result)
            assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        }
    }

    @Test fun legacyCoordinateModeMapsBothFramesWithoutChangingVisibility() {
        fun read(value: String, type: String = "switch") = LegacySettingsReader.read(
            "(panel p (item mode :target attitudeIndicatorInertialMode :type $type :value $value))")
        val current = AppSettings(hudAttitude = false, hudFields = listOf("future", "heading"))
        for (mode in listOf(false, true)) {
            val imported = read(mode.toString())
            assertTrue(imported.hasChanges)
            val applied = imported.applyTo(current)
            assertEquals(current.copy(hudCompassHeadingUp = mode, hudAttitudeEarthFixed = mode), applied)
            assertEquals(applied, SettingsJson.decode(SettingsJson.encode(applied)))
        }
        assertEquals(true, read("false", "switch-inv").hudCompassHeadingUp)
        assertFails { read("true", "data") }
        assertFails { read("yes") }
    }

    @Test fun attitudeCompassSwitchPreservesEnabledDataPanelHeading() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(key: String, value: Boolean) = "(item x :target $key :type switch :value $value)"
        for (master in listOf(false, true)) for (attitude in listOf(false, true)) for (data in listOf(false, true)) {
            val imported = read(flag("drawHUDtext", master) + flag("showAttitudeGauge", attitude) +
                "(item h :target getCompass :type data :value $data)")
            assertEquals(data || (master && !attitude), imported.hudFieldChoices["heading"])
            assertEquals(master && attitude, imported.hudAttitude)
        }
        val current = AppSettings(hudFields = listOf("future", "ias"))
        val applied = read(flag("showAttitudeGauge", false)).applyTo(current)
        assertFalse(applied.hudAttitude)
        assertEquals(listOf("future", "ias", "heading"), applied.hudFields)
        assertEquals(applied, SettingsJson.decode(SettingsJson.encode(applied)))
        assertEquals(current.copy(hudAttitude = true), read(flag("showAttitudeGauge", true)).applyTo(applied))
    }

    @Test fun attitudeImportHonorsTextMasterAndLegacyDefault() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(key: String, value: Boolean) = "(item x :target $key :type switch :value $value)"
        for (master in listOf(false, true)) for (attitude in listOf(false, true)) {
            assertEquals(master && attitude, read(flag("drawHUDtext", master) +
                flag("showAttitudeGauge", attitude)).hudAttitude)
        }
        assertEquals(false, read(flag("drawHUDtext", false)).hudAttitude)
        assertEquals(true, read(flag("drawHUDtext", true)).hudAttitude)
        assertEquals(false, read(flag("showAttitudeGauge", false)).hudAttitude)
        val unrelated = read(flag("showHUDAoA", false))
        assertNull(unrelated.hudAttitude)
        assertTrue(unrelated.applyTo(AppSettings(hudAttitude = true)).hudAttitude)
        assertFalse(unrelated.applyTo(AppSettings(hudAttitude = false)).hudAttitude)
    }

    @Test fun maneuverBarImportNamesItsFuelMassMeaningAndPreservesOtherFields() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(key: String, value: Boolean) = "(item x :target $key :type switch :value $value)"
        for (master in listOf(false, true)) for (enabled in listOf(false, true)) {
            assertEquals(master && enabled, read(flag("drawHUDtext", master) +
                flag("showHUDManeuverBar", enabled)).hudFieldChoices["fuel_mass_share"])
        }
        val imported = read(flag("showHUDManeuverBar", true))
        assertTrue(imported.unmigrated.isEmpty())
        val current = AppSettings(hudFields = listOf("future", "load"))
        val settings = imported.applyTo(current)
        assertEquals(listOf("future", "load", "fuel_mass_share"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(current, read(flag("showHUDManeuverBar", false)).applyTo(settings))
        assertEquals(false, read("(item x :target showHUDManeuverBar :type switch-inv :value true)").hudFieldChoices["fuel_mass_share"])
        assertFails { read("(item x :target showHUDManeuverBar :type data :value true)") }
        assertFails { read("(item x :target showHUDManeuverBar :type switch :value yes)") }
    }

    @Test fun flapBarImportPreservesNumericPreferencesAndHonorsMaster() {
        fun read(value: String, extra: String = "") = LegacySettingsReader.read(
            "(panel p (item b :target enableFlapAngleBar :type switch :value $value) $extra)")
        for (enabled in listOf(false, true)) {
            val imported = read(enabled.toString())
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            val current = AppSettings(hudFlaps = false)
            val settings = imported.applyTo(current)
            assertEquals(current.copy(hudFlapBar = enabled), settings)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(false, read("true", "(item m :target drawHUDtext :type switch :value false)").hudFlapBar)
        assertFails { read("yes") }
        assertTrue(SettingsJson.decode("""{"version":1}""").hudFlapBar)
    }

    @Test fun speedBarSwitchSelectsSpeedOrThrottleAndHonorsTextMaster() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(key: String, value: Boolean) = "(item x :target $key :type switch :value $value)"
        for (master in listOf(false, true)) for (speed in listOf(false, true)) {
            val settings = read(flag("drawHUDtext", master) + flag("showSpeedBar", speed))
            assertEquals(master && speed, settings.hudFieldChoices["speed_limit_ratio"])
            assertEquals(master && !speed, settings.hudFieldChoices["engine1_throttle"])
        }
        val current = AppSettings(hudFields = listOf("future", "speed_limit_ratio", "ias"))
        val throttle = read(flag("showSpeedBar", false))
        assertTrue(throttle.unmigrated.isEmpty())
        val applied = throttle.applyTo(current)
        assertEquals(listOf("future", "ias", "engine1_throttle"), applied.hudFields)
        assertEquals(applied, SettingsJson.decode(SettingsJson.encode(applied)))
        assertEquals(listOf("future", "ias", "speed_limit_ratio"), read(flag("showSpeedBar", true)).applyTo(applied).hudFields)
        assertEquals(throttle.hudFieldChoices, read("(item x :target showSpeedBar :type switch-inv :value true)").hudFieldChoices)
        assertFails { read("(item x :target showSpeedBar :type data :value true)") }
        assertFails { read("(item x :target showSpeedBar :type switch :value yes)") }
    }

    @Test fun aoaNumericThresholdImportsLegacyRatioAndPercentageUnits() {
        fun read(value: String, type: String = "slider") = LegacySettingsReader.read(
            "(panel p (item threshold :target miniHUDaoaWarningRatio :type $type :value $value))")
        val current = AppSettings(hudAoaWarningPercent = 30.0)
        for ((value, expected) in listOf("0" to 0.0, "0.255" to 25.5, "1" to 100.0,
            "1.5" to 1.5, "25" to 25.0, "100" to 100.0)) {
            val imported = read(value)
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(expected, imported.hudAoaWarningPercent)
            val settings = imported.applyTo(current)
            assertEquals(current.copy(hudAoaWarningPercent = expected), settings)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(25.0, read("0.25", "input").hudAoaWarningPercent)
        for (bad in listOf("NaN", "Infinity", "-1", "101", "bad")) assertFails { read(bad) }
        assertFails { read("25", "switch") }
    }

    @Test fun aoaBarThresholdImportsLegacyRatioAndPercentageUnits() {
        fun read(value: String, type: String = "slider") = LegacySettingsReader.read(
            "(panel p (item threshold :target miniHUDaoaBarWarningRatio :type $type :value $value))")
        val current = AppSettings(hudAoaBarWarningPercent = 30.0)
        for ((value, expected) in listOf("0" to 0.0, "0.255" to 25.5, "1" to 100.0,
            "1.5" to 1.5, "25" to 25.0, "100" to 100.0)) {
            val imported = read(value)
            assertTrue(imported.hasChanges)
            assertTrue(imported.unmigrated.isEmpty())
            assertEquals(expected, imported.hudAoaBarWarningPercent)
            val settings = imported.applyTo(current)
            assertEquals(current.copy(hudAoaBarWarningPercent = expected), settings)
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        assertEquals(25.0, read("0.25", "input").hudAoaBarWarningPercent)
        for (bad in listOf("NaN", "Infinity", "-1", "101", "bad")) assertFails { read(bad) }
        assertFails { read("25", "switch") }
    }

    @Test fun aoaImportPreservesAttitudeAndUsesTextMaster() {
        val current = AppSettings(hudFields = listOf("future", "aoa", "fuel"), hudAttitude = true)
        val imported = LegacySettingsReader.read("""(panel p
            (item aoa :target showHUDAoA :type switch-inv :value true))""")
        assertTrue(imported.hasChanges)
        assertTrue(imported.unmigrated.isEmpty())
        val result = imported.applyTo(current)
        assertEquals(current.copy(hudFields = listOf("future", "fuel")), result)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        val gated = LegacySettingsReader.read("""(panel p
            (item master :target drawHUDtext :type switch :value false)
            (item aoa :target showHUDAoA :type switch :value true))""")
        assertEquals(false, gated.hudFieldChoices["aoa"])
        assertFails { LegacySettingsReader.read("(panel p (item a :target showHUDAoA :type data :value true))") }
        assertFails { LegacySettingsReader.read("(panel p (item a :target showHUDAoA :type switch :value yes))") }
    }

    @Test fun mechanicalSwitchesRespectTextMasterAndPreserveUnspecifiedPreferences() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(target: String, value: Boolean) = "(item x :target $target :type switch :value $value)"
        for (master in listOf(false, true)) for (child in listOf(false, true)) {
            val imported = read(flag("drawHUDtext", master) + flag("showHUDGear", child) +
                flag("showHUDFlaps", child) + flag("showHUDAirbrake", child))
            assertEquals(master && child, imported.hudGear)
            assertEquals(master && child, imported.hudFlaps)
            assertEquals(master && child, imported.hudAirbrake)
        }
        val current = AppSettings(hudMechanization = false, hudFlaps = false, hudAirbrake = false)
        val only = read(flag("showHUDGear", false))
        assertTrue(only.hasChanges)
        assertTrue(only.unmigrated.isEmpty())
        assertEquals(current.copy(hudGear = false), only.applyTo(current))
        assertEquals(only.applyTo(current), SettingsJson.decode(SettingsJson.encode(only.applyTo(current))))
        assertEquals(true, read(flag("drawHUDtext", true)).hudFlaps)
        assertEquals(false, read(flag("drawHUDtext", false)).hudAirbrake)
        assertEquals(true, read("(item b :target showHUDAirbrake :type switch-inv :value false)").hudAirbrake)
        assertFails { read("(item b :target showHUDFlaps :type data :value true)") }
        assertFails { read("(item b :target showHUDGear :type switch :value yes)") }
    }

    @Test fun miniHudTextMasterGatesNumericFieldsButPreservesDataPanel() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(target: String, value: Boolean) = "(item x :target $target :type switch :value $value)"
        for (master in listOf(false, true)) for (child in listOf(false, true)) for (data in listOf(false, true)) {
            val imported = read(flag("drawHUDtext", master) + flag("showHUDAltitude", child) +
                flag("showHUDSpeed", child) + flag("hudMach", true) +
                "(item a :target getAltitude :type data :value $data)(item m :target getMach :type data :value $data)")
            assertEquals(data || (master && child), imported.hudFieldChoices["altitude"])
            assertEquals(data || (master && child), imported.hudFieldChoices["mach"])
            assertEquals(false, imported.hudFieldChoices["ias"])
        }
        val hidden = read(flag("drawHUDtext", false))
        assertEquals(setOf("altitude", "energy", "sep", "load", "aoa", "ias", "mach", "speed_limit_ratio", "engine1_throttle", "fuel_mass_share", "heading"), hidden.hudFieldChoices.keys)
        assertTrue(hidden.hudFieldChoices.values.none { it })
        val current = AppSettings(hudFields = listOf("future", "ias", "altitude", "fuel"))
        assertEquals(listOf("future", "fuel"), hidden.applyTo(current).hudFields)
        assertEquals(mapOf("altitude" to true, "energy" to true, "sep" to true, "load" to true, "aoa" to true,
            "ias" to true, "mach" to false, "speed_limit_ratio" to true, "engine1_throttle" to false, "fuel_mass_share" to true, "heading" to false), read(flag("drawHUDtext", true)).hudFieldChoices)
        assertEquals(hidden.hudFieldChoices,
            read("(item m :target drawHUDtext :type switch-inv :value true)").hudFieldChoices)
        assertFails { read("(item m :target drawHUDtext :type switch :value yes)") }
        assertFails { read("(item m :target drawHUDtext :type data :value false)") }
    }

    @Test fun speedVisibilityAndMachModeUseLegacyDefaultsAndPreserveDataPanelChoices() {
        fun read(items: String) = LegacySettingsReader.read("(panel p $items)")
        fun flag(target: String, value: Boolean) = "(item x :target $target :type switch :value $value)"
        for (visible in listOf(false, true)) for (mach in listOf(false, true)) {
            val imported = read(flag("showHUDSpeed", visible) + flag("hudMach", mach))
            assertEquals(visible && !mach, imported.hudFieldChoices["ias"])
            assertEquals(visible && mach, imported.hudFieldChoices["mach"])
            assertTrue(imported.unmigrated.isEmpty())
        }
        assertEquals(mapOf("ias" to true, "mach" to false), read(flag("showHUDSpeed", true)).hudFieldChoices)
        assertEquals(mapOf("ias" to false, "mach" to true), read(flag("hudMach", true)).hudFieldChoices)
        val combined = read(flag("showHUDSpeed", false) +
            "(item ias :target getIAS :type data :value true)(item mach :target getMach :type data :value true)")
        assertEquals(mapOf("ias" to true, "mach" to true), combined.hudFieldChoices)
        val settings = read(flag("hudMach", true)).applyTo(AppSettings(hudFields = listOf("future", "ias", "fuel")))
        assertEquals(listOf("future", "fuel", "mach"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFails { read("(item speed :target showHUDSpeed :type switch :value yes)") }
        assertFails { read("(item mode :target hudMach :type data :value true)") }
    }

    @Test fun miniHudNumericSwitchesMergeWithLegacyDataPanelByVisibility() {
        for ((mini, data, id) in listOf(Triple("showHUDAltitude", "getAltitude", "altitude"),
            Triple("showHUDSep", "getSEP", "sep"), Triple("showHUDGLoad", "getNy", "load"),
            Triple("showHUDAoA", "getAoA", "aoa"))) {
            for (a in listOf(false, true)) for (b in listOf(false, true)) {
                val imported = LegacySettingsReader.read("""(panel p
                    (item mini :target $mini :type switch :value $a)
                    (item data :target $data :type data :value $b))""")
                assertEquals(a || b, imported.hudFieldChoices[id])
                assertTrue(imported.unmigrated.isEmpty())
            }
        }
        val only = LegacySettingsReader.read("""(panel p
            (item energy :target showHUDEnergy :type switch-inv :value false)
            (item height :target showHUDAltitude :type switch :value false))""")
        val settings = only.applyTo(AppSettings(hudFields = listOf("future", "altitude", "fuel")))
        assertEquals(listOf("future", "fuel", "energy"), settings.hudFields)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertFails { LegacySettingsReader.read("(panel p (item e :target showHUDEnergy :type data :value true))") }
        assertFails { LegacySettingsReader.read("(panel p (item e :target showHUDEnergy :type switch :value 1))") }
    }

    @Test fun loggingPreferenceMapsWithoutOverwritingRecordingLocation() {
        val current = AppSettings(recordingDirectory = "custom-records", recordingAutoStart = false)
        fun read(type: String, value: String) = LegacySettingsReader.read(
            "(panel p (item logging :target enableLogging :type $type :value $value))")
        val enabled = read("switch", "true")
        assertTrue(enabled.hasChanges)
        assertTrue(enabled.unmigrated.isEmpty())
        val settings = enabled.applyTo(current)
        assertEquals(current.copy(recordingAutoStart = true), settings)
        assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        assertEquals(current, read("switch", "false").applyTo(settings))
        assertEquals(settings, read("switch-inv", "false").applyTo(current))
        val other = LegacySettingsReader.read("(panel p (item interval :target Interval :type input :value 100))")
        assertTrue(other.applyTo(settings).recordingAutoStart)
        for ((type, value) in listOf("data" to "true", "switch" to "yes", "input" to "1")) {
            assertFails { read(type, value) }
        }
    }

    @Test fun unsupportedItemsAreReportedWithoutEvaluatingOrApplyingTheirValues() {
        val unsupported = """(item "自定义字体" :target fontChoice :type input :value (run arbitrary))"""
        val report = LegacySettingsReader.read("(panel p $unsupported)")
        assertFalse(report.hasChanges)
        assertEquals(listOf(UnmigratedLegacySetting("自定义字体", "fontChoice")), report.unmigrated)
        val current = AppSettings(hudOpacity = .4f)
        assertEquals(current, report.applyTo(current))
        val mixed = LegacySettingsReader.read("""(panel p $unsupported
            (item speed :target getIAS :type data :value false))""")
        assertTrue(mixed.hasChanges)
        assertEquals(report.unmigrated, mixed.unmigrated)
        assertFalse("ias" in mixed.applyTo(current).hudFields)
    }

    @Test fun focusHidingImportsIndependentlyAndPreservesUnspecifiedPreferences() {
        for (choice in listOf(false, true)) {
            val imported = LegacySettingsReader.read("""(panel p
                (item focus :type switch :target autoHideOnFocusLoss :value $choice))""")
            val current = AppSettings(hudAutoHideOnFocusLoss = !choice, hudEnabled = true, hudClickThrough = true)
            val updated = imported.applyTo(current)
            assertEquals(current.copy(hudAutoHideOnFocusLoss = choice), updated)
            assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
        }
        val unrelated = LegacySettingsReader.read("""(panel p (item x :type slider :target Interval :value 100))""")
        assertTrue(unrelated.applyTo(AppSettings(hudAutoHideOnFocusLoss = true)).hudAutoHideOnFocusLoss)
        val inverted = LegacySettingsReader.read("""(panel p (item x :type switch-inv :target autoHideOnFocusLoss :value false))""")
        assertEquals(true, inverted.hudAutoHideOnFocusLoss)
    }

    @Test fun focusHidingRejectsInvalidOrDuplicateLegacyValues() {
        for (entry in listOf(":type switch :value 1", ":type input :value true", ":type switch :value nope")) {
            assertFails { LegacySettingsReader.read("(panel p (item x :target autoHideOnFocusLoss $entry))") }
        }
        assertFails { LegacySettingsReader.read("""(panel p
            (item a :target autoHideOnFocusLoss :type switch :value true)
            (item b :target autoHideOnFocusLoss :type switch :value false))""") }
    }

    @Test fun importsTotalPowerAndFuelChoicesWithoutChangingThrustSelection() {
        val imported = LegacySettingsReader.read("""
            (panel p
                (item power :type data :target getHorsePower :value true)
                (item fuel :type data :target getMassFuel :value false)
                (item thrust :type data :target getThrust :value false))
        """)
        assertEquals(mapOf("power" to true, "fuel" to false, "engine1_thrust" to false), imported.hudFieldChoices)
        val current = AppSettings(hudFields = listOf("fuel", "thrust", "future", "ias"))
        val updated = imported.applyTo(current)
        assertEquals(listOf("thrust", "future", "ias", "power"), updated.hudFields)
        assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
    }

    @Test fun dataSwitchesMergeWithoutReplacingUnspecifiedFieldsAndUseMiniHudAttitudeFlag() {
        val imported = LegacySettingsReader.read("""(panel p
            (item x :target "getAoS" :type data :value true)
            (item x :target "getIAS" :type data :value false)
            (item x :target "getRollRate" :type data :value true)
            (item x :target "showAttitudeGauge" :type switch :value false)
            (item x :target "enableAttitudeIndicator" :type switch :value true))""")
        val current = AppSettings(hudFields = listOf("ias", "future", "roll_rate", "fuel"))
        val result = imported.applyTo(current)
        assertEquals(listOf("future", "roll_rate", "fuel", "sideslip", "heading"), result.hudFields)
        assertFalse(result.hudAttitude)
        assertEquals(result, SettingsJson.decode(SettingsJson.encode(result)))
        assertEquals(result, imported.applyTo(result))
    }

    @Test fun explicitAllHiddenDoesNotFallBackToDefaultAndBadDataSwitchesAreRejected() {
        val imported = LegacySettingsReader.read("""(panel p (item x :target "getAoS" :type data :value false))""")
        assertTrue(imported.applyTo(AppSettings(hudFields = listOf("sideslip"))).hudFields.isEmpty())
        for (entry in listOf(":type data :value 1", ":type switch :value true", ":type data :value nope")) {
            assertFails { LegacySettingsReader.read("(panel p (item x :target getAoS $entry))") }
        }
        assertFails { LegacySettingsReader.read("""(panel p
            (item x :target getAoS :type data :value true)
            (item x :target getAoS :type data :value false))""") }
    }

    @Test fun legacyPerAlertChoicesRetainPackAndEnabledState() {
        val imported = LegacySettingsReader.read("""(panel p
            (item x :target "voice_aoaCrit" :type voice :value "中文包|false")
            (item x :target "voice_warn_ias" :type voice :value "")
            (item x :target "voice_warn_mach" :type voice :value "custom"))""")
        assertEquals(mapOf("aoaCrit" to VoiceChoice(false, "中文包"),
            "warn_ias" to VoiceChoice(true, "default"), "warn_mach" to VoiceChoice(true, "custom")), imported.alertVoices)
        val current = AppSettings(alertVoices = mapOf("warn_lowfuel" to VoiceChoice(false)))
        assertEquals(VoiceChoice(false), imported.applyTo(current).alertVoices["warn_lowfuel"])
        for (value in listOf("../escape|true", "pack|invalid", "pack|true|false")) assertFails {
            LegacySettingsReader.read("""(panel p (item x :target "voice_aoaCrit" :type voice :value "$value"))""")
        }
    }

    @Test fun volumeImportsIndependentlyAndRejectsInvalidValues() {
        fun document(volume: String) = """(panel p (item x :target "voiceVolume" :type slider :value $volume))"""
        for (volume in listOf(0, 100, 200)) {
            val imported = LegacySettingsReader.read(document(volume.toString()))
            assertEquals(AppSettings(voiceVolume = volume), imported.applyTo(AppSettings()))
        }
        for (value in listOf("-1", "201", "NaN", "10.5")) assertFails { LegacySettingsReader.read(document(value)) }
    }

    @Test fun importsNestedItemsWithPriorityAndPreservesOtherSettings() {
        val imported = LegacySettingsReader.read("""
            ; :target fake in comment
            (panel "中文 (标题)"
              (group "设置"
                (item "旧频率" :type input :target "Interval" :value 50)
                (item "新频率" :type slider :target "dataPollIntervalMs" :value 80)
                (item "HUD" :type switch-inv :target "crosshairSwitch" :value false)
                (item "语音" :type switch :target "enableVoiceWarn" :value false)
                (item "无关" :type input :target "custom" :value "(; :target fake)")))
        """.trimIndent())
        val current = AppSettings(endpoint = "http://localhost:9222", hudOpacity = .4f, voiceEnabled = true)
        assertEquals(current.copy(pollIntervalMs = 80, hudEnabled = true, voiceEnabled = false), imported.applyTo(current))
    }

    @Test fun partialImportsDoNotSupplyDefaults() {
        val result = LegacySettingsReader.read("""(panel "p" (item "x" :target "Interval" :type slider :value 250))""")
        assertEquals(LegacySettings(250, null, null), result)
    }

    @Test fun rejectsAmbiguityBadValuesAndMalformedDocuments() {
        fun item(value: String) = """(item "x" :target "dataPollIntervalMs" :type slider :value $value)"""
        for (text in listOf(
            "(panel p ${item("9")})", "(panel p ${item("NaN")})",
            "(panel p ${item("100")} ${item("200")})", "(panel p ${item("100")}",
            "(panel p)", "[settings]", "(panel p (item \"unterminated))",
            """(panel p (item x :target "enableVoiceWarn" :type switch :value yes))""",
        )) assertFails(text) { LegacySettingsReader.read(text) }
    }

    @Test fun rejectsExcessiveNestingAndSize() {
        assertFails { LegacySettingsReader.read("(panel p " + "(".repeat(70) + ")".repeat(71)) }
        assertFails { LegacySettingsReader.read(" ".repeat(LegacySettingsReader.MAX_BYTES + 1)) }
    }
}
