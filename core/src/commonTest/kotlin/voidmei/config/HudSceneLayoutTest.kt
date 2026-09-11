package voidmei.config

import kotlin.test.*

class HudSceneLayoutTest {
    @Test fun messageLineLimitPersistsAndOldLayoutsKeepFullText() {
        val scene = HudSceneLayout(240, 400, listOf(HudRegion("messages", HudRegionContent.MESSAGES,
            0, 0, 240, 400, messageMaxLines = 3)))
        val settings = AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("messages" to scene))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertEquals(settings.hudScenePresets, HudPresetFile.decode(HudPresetFile.encode(settings.hudScenePresets)))
        assertEquals(0, SettingsJson.decode(json.replace(",\"messageMaxLines\":3", "")).hudSceneLayout!!.regions.single().messageMaxLines)
        for (bad in listOf("null", "-1", "11", "1.5", "\"3\"", "true"))
            assertFails { SettingsJson.decode(json.replace("\"messageMaxLines\":3", "\"messageMaxLines\":$bad")) }
    }
    @Test fun optionalControlStickPersistsWithoutChangingOldLayouts() {
        val region = HudRegion("controls", HudRegionContent.CONTROLS, 0, 0, 240, 400, showControlStick = true)
        val scene = HudSceneLayout(240, 400, listOf(region))
        val settings = AppSettings(hudSceneLayout = scene, hudScenePresets = mapOf("操纵面" to scene))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertEquals(settings.hudScenePresets, HudPresetFile.decode(HudPresetFile.encode(settings.hudScenePresets)))
        assertFalse(SettingsJson.decode(json.replace(",\"showControlStick\":true", "")).hudSceneLayout!!.regions.single().showControlStick)
        for (bad in listOf("null", "0", "\"true\""))
            assertFails { SettingsJson.decode(json.replace("\"showControlStick\":true", "\"showControlStick\":$bad")) }
    }

    @Test fun engineInstrumentsPersistAndOldLayoutsKeepThem() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 0, 0, 240, 120, showEngineInstruments = false)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region)))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertTrue(SettingsJson.decode(json.replace(",\"showEngineInstruments\":false", "")).hudSceneLayout!!.regions.single().showEngineInstruments)
        for (bad in listOf("null", "0", "\"false\""))
            assertFails { SettingsJson.decode(json.replace("\"showEngineInstruments\":false", "\"showEngineInstruments\":$bad")) }
    }

    @Test fun flightStatusVisibilityPersistsAndOldLayoutsKeepTheTitle() {
        val region = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 240, 120, showFlightStatus = false)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region)))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertTrue(SettingsJson.decode(json.replace(",\"showFlightStatus\":false", "")).hudSceneLayout!!.regions.single().showFlightStatus)
        assertFails { SettingsJson.decode(json.replace("\"showFlightStatus\":false", "\"showFlightStatus\":\"false\"")) }
    }

    @Test fun messageLimitPersistsAndDefaultsForOldLayouts() {
        val region = HudRegion("messages", HudRegionContent.MESSAGES, 0, 0, 240, 120, messageLimit = 20)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region)))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertEquals(5, SettingsJson.decode(json.replace(",\"messageLimit\":20", "")).hudSceneLayout!!.regions.single().messageLimit)
        for (bad in listOf("0", "21", "null", "\"5\""))
            assertFails { SettingsJson.decode(json.replace("\"messageLimit\":20", "\"messageLimit\":$bad")) }
    }

    @Test fun optionalFlightInstrumentsPersistAndOldLayoutsKeepThem() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120, showFlightInstruments = false)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region)))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertTrue(SettingsJson.decode(json.replace(",\"showFlightInstruments\":false", "")).hudSceneLayout!!.regions.single().showFlightInstruments)
        assertFails { SettingsJson.decode(json.replace("\"showFlightInstruments\":false", "\"showFlightInstruments\":\"false\"")) }
    }

    @Test fun controlsRegionCanBeAddedAndSavedOnSmallAndNormalCanvases() {
        val scene = HudSceneLayout.initial(AppSettings()).addRegion(HudRegionContent.CONTROLS)
        assertEquals(440, scene.regions.last().width)
        assertEquals(260, scene.regions.last().height)
        val small = scene.resizeCanvas(240, 120).addRegion(HudRegionContent.CONTROLS)
        assertEquals(120, small.regions.last().height)
        for (layout in listOf(scene, small))
            assertEquals(layout, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = layout))).hudSceneLayout)
    }

    @Test fun regionFontScaleDefaultsToInheritanceAndPersistsAcrossCopies() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        for (scale in listOf(null, .75f, 1.5f, 2f)) {
            val scene = HudSceneLayout(500, 300, listOf(region.copy(fontScale = scale)))
            assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
            assertEquals(scale, scene.duplicateRegion("one").regions.last().fontScale)
        }
        val json = kotlinx.serialization.json.Json.parseToJsonElement(
            SettingsJson.encode(AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region))))).toString()
        assertNull(SettingsJson.decode(json.replace(",\"fontScale\":null", "")).hudSceneLayout!!.regions.single().fontScale)
        for (bad in listOf(.74f, 2.01f, Float.NaN, Float.POSITIVE_INFINITY))
            assertFailsWith<IllegalArgumentException> { region.copy(fontScale = bad) }
        assertFails { SettingsJson.decode(json.replace("\"fontScale\":null", "\"fontScale\":\"1.5\"")) }
    }

    @Test fun compassRegionsPersistAndFitSmallCanvases() {
        val scene = HudSceneLayout.initial(AppSettings()).addRegion(HudRegionContent.COMPASS)
        assertEquals(240, scene.regions.last().width)
        assertEquals(240, scene.regions.last().height)
        val small = scene.resizeCanvas(240, 120).addRegion(HudRegionContent.COMPASS)
        assertEquals(120, small.regions.last().height)
        assertEquals(small, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = small))).hudSceneLayout)
    }

    @Test fun duplicatesKeepAllOptionsAndStayInsideCanvasWithoutChangingExistingRegions() {
        val source = HudRegion("region-1", HudRegionContent.ENGINE, 300, 200, 200, 200, .2f, .8f,
            2, listOf("rpm", "future"), visible = false, title = "右发动机", readingColumns = 1)
        val other = HudRegion("other", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        val scene = HudSceneLayout(500, 400, listOf(source, other), displayId = "display")
        val copied = scene.duplicateRegion(source.id)
        assertEquals(listOf(source, source.copy(id = "region-2", x = 284, y = 184), other), copied.regions)
        assertEquals("display", copied.displayId)
        assertEquals(copied, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = copied))).hudSceneLayout)
        val fullCanvas = HudSceneLayout(240, 120, listOf(other))
        assertEquals(other.copy(id = "region-1"), fullCanvas.duplicateRegion("other").regions.last())
        assertFailsWith<IllegalArgumentException> { scene.duplicateRegion("missing") }
        var full = scene
        repeat(30) { full = full.duplicateRegion(source.id) }
        assertEquals(32, full.regions.map { it.id }.toSet().size)
        assertFailsWith<IllegalArgumentException> { full.duplicateRegion(source.id) }
    }

    @Test fun regionalColumnsDistinguishInheritanceFromAutomaticAndPersist() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        for (columns in listOf(null, 0, 1, 2)) {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(240, 120, listOf(region.copy(readingColumns = columns))))
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
        val oldJson = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(AppSettings(
            hudSceneLayout = HudSceneLayout(240, 120, listOf(region))))).toString().replace(",\"readingColumns\":null", "")
        assertNull(SettingsJson.decode(oldJson).hudSceneLayout!!.regions.single().readingColumns)
        for (invalid in listOf(-1, 3)) assertFailsWith<IllegalArgumentException> { region.copy(readingColumns = invalid) }
    }

    @Test fun regionTitlesPersistThroughEditingAndOldLayoutsDefaultToEmpty() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 0, 0, 240, 120, title = "能量与机动")
        val scene = HudSceneLayout(500, 400, listOf(region))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).toString()
        assertEquals(scene, SettingsJson.decode(json).hudSceneLayout)
        assertEquals("", SettingsJson.decode(json.replace(",\"title\":\"能量与机动\"", "")).hudSceneLayout!!.regions.single().title)
        assertEquals(region.title, scene.moveRegion("one", 100, 100).resizeRegion("one", 300, 200).regions.single().title)
        assertFailsWith<IllegalArgumentException> { region.copy(title = "x".repeat(81)) }
        assertFailsWith<IllegalArgumentException> { region.copy(title = "a\nb") }
        assertFails { SettingsJson.decode(json.replace("\"title\":\"能量与机动\"", "\"title\":7")) }
    }

    @Test fun crosshairRegionDefaultsToTransparentAndPersistsGeometry() {
        val scene = HudSceneLayout.initial(AppSettings()).addRegion(HudRegionContent.CROSSHAIR)
        val region = scene.regions.last()
        assertEquals(HudRegionContent.CROSSHAIR, region.content)
        assertEquals(128, region.width)
        assertEquals(128, region.height)
        assertEquals(0f, region.backgroundAlpha)
        assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
    }

    @Test fun restoringRemovedRegionPreservesLaterEditsAndHandlesReusedIdsAndSmallerCanvas() {
        val removed = HudRegion("region-1", HudRegionContent.ENGINE, 500, 300, 300, 200,
            .25f, .75f, 2, listOf("rpm", "future_field"), visible = false)
        val other = HudRegion("other", HudRegionContent.FLIGHT, 0, 0, 200, 100)
        val scene = HudSceneLayout(800, 600, listOf(removed, other), displayId = "display")
        assertEquals(scene, scene.removeRegion(removed.id).restoreRegion(removed, 0))
        val edited = scene.removeRegion(removed.id).addRegion(HudRegionContent.MAP).resizeCanvas(240, 120)
        val restored = edited.restoreRegion(removed, 0)
        assertEquals(edited.regions, restored.regions.drop(1))
        assertEquals(removed.copy(id = "region-2", x = 0, y = 0, width = 240, height = 120), restored.regions.first())
        assertEquals("display", restored.displayId)
        assertEquals(restored, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = restored))).hudSceneLayout)
        var full = edited
        repeat(30) { full = full.addRegion(HudRegionContent.FLIGHT) }
        assertFailsWith<IllegalArgumentException> { full.restoreRegion(removed, 0) }
    }

    @Test fun mapRegionCanBeRepeatedAndPersistedWithIndependentAppearance() {
        val scene = HudSceneLayout.initial(AppSettings()).addRegion(HudRegionContent.MAP).addRegion(HudRegionContent.MAP)
        val maps = scene.regions.filter { it.content == HudRegionContent.MAP }
        assertEquals(2, maps.size)
        assertNotEquals(maps[0].id, maps[1].id)
        assertEquals(500, maps[0].height)
        assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
    }

    @Test fun hiddenRegionsPersistAndOldConfigurationsRemainVisible() {
        val region = HudRegion("engine", HudRegionContent.ENGINE, 20, 30, 200, 150,
            .25f, .75f, 2, listOf("rpm", "future_field"), visible = false)
        val settings = AppSettings(hudSceneLayout = HudSceneLayout(500, 400, listOf(region)))
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SettingsJson.encode(settings)).toString()
        assertEquals(settings, SettingsJson.decode(json))
        assertEquals(region.copy(visible = true), SettingsJson.decode(json.replace(",\"visible\":false", ""))
            .hudSceneLayout!!.regions.single())
        assertFalse(settings.hudSceneLayout!!.resizeRegion("engine", 300, 250).regions.single().visible)
        for (invalid in listOf("\"false\"", "null", "0")) {
            assertFails { SettingsJson.decode(json.replace("\"visible\":false", "\"visible\":$invalid")) }
        }
    }

    @Test fun messageRegionCanBeAddedAndPersisted() {
        val scene = HudSceneLayout.initial(AppSettings()).addRegion(HudRegionContent.MESSAGES)
        assertEquals(HudRegionContent.MESSAGES, scene.regions.last().content)
        assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
    }

    @Test fun selectedDisplaySurvivesCanvasAndRegionChanges() {
        val scene = HudSceneLayout.initial(AppSettings()).copy(displayId = "external-display")
        assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
        assertEquals("external-display", scene.resizeCanvas(1920, 1080).displayId)
        assertEquals("external-display", scene.addRegion(HudRegionContent.ENGINE).displayId)
    }

    @Test fun resizeRegionKeepsOriginAndLimitsSizeToRemainingCanvas() {
        val region = HudRegion("one", HudRegionContent.ENGINE, 100, 50, 200, 150, .25f, .75f, 2, listOf("rpm"))
        val scene = HudSceneLayout(500, 400, listOf(region))
        assertEquals(region.copy(width = 400, height = 350), scene.resizeRegion("one", Int.MAX_VALUE, Int.MAX_VALUE).regions.single())
        assertEquals(region.copy(width = 80, height = 40), scene.resizeRegion("one", Int.MIN_VALUE, Int.MIN_VALUE).regions.single())
        val resized = scene.resizeRegion("one", 300, 250)
        assertEquals(resized, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = resized))).hudSceneLayout)
    }

    @Test fun movingRegionsClampsToCanvasAndPreservesOtherProperties() {
        val scene = HudSceneLayout.initial(AppSettings(hudEngineIndex = 2))
        val first = scene.regions.first()
        val moved = scene.moveRegion(first.id, Int.MAX_VALUE, Int.MIN_VALUE)
        assertEquals(first.copy(x = scene.width - first.width, y = 0), moved.regions.first())
        assertEquals(scene.regions.drop(1), moved.regions.drop(1))
        assertEquals(moved, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = moved))).hudSceneLayout)
    }

    @Test fun layerMovesPreserveRegionsAndPersistPaintingOrder() {
        val scene = HudSceneLayout.initial(AppSettings(hudEngineIndex = 2))
        val first = scene.regions.first()
        val moved = scene.moveRegionLayer(first.id, true)
        assertEquals(listOf(scene.regions[1], first) + scene.regions.drop(2), moved.regions)
        assertEquals(scene, moved.moveRegionLayer(first.id, false))
        assertSame(scene, scene.moveRegionLayer(first.id, false))
        assertSame(scene, scene.moveRegionLayer(scene.regions.last().id, true))
        assertSame(scene, scene.moveRegionLayer("missing", true))
        assertEquals(moved, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = moved))).hudSceneLayout)
    }

    @Test fun independentFieldsPreserveUnknownIdsOrderAndEmptySelection() {
        val base = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 240, 120)
        for (fields in listOf(null, emptyList(), listOf("altitude", "future_field", "ias"))) {
            val scene = HudSceneLayout(240, 120, listOf(base.copy(fields = fields)))
            val saved = AppSettings(hudSceneLayout = scene)
            assertEquals(saved, SettingsJson.decode(SettingsJson.encode(saved)))
            assertEquals(fields, scene.resizeCanvas(800, 600).regions.single().fields)
            assertEquals(fields, scene.addRegion(HudRegionContent.ENGINE).regions.first().fields)
        }
    }

    @Test fun canvasResizePreservesUnchangedGeometryAndKeepsAllRegionsInside() {
        val scene = HudSceneLayout.initial(AppSettings(hudEngineIndex = 2)).copy(enabled = false)
        assertEquals(scene.regions, scene.resizeCanvas(1920, 1080).regions)
        val small = scene.resizeCanvas(240, 120)
        assertFalse(small.enabled)
        assertEquals(scene.regions.map { it.id }, small.regions.map { it.id })
        scene.regions.zip(small.regions).forEach { (before, after) ->
            assertTrue(after.x + after.width <= 240 && after.y + after.height <= 120)
            assertEquals(before.copy(x = after.x, y = after.y, width = after.width, height = after.height), after)
        }
        val edge = HudSceneLayout(800, 600, listOf(HudRegion("edge", HudRegionContent.FLIGHT, 600, 400, 200, 200)))
        assertEquals(HudRegion("edge", HudRegionContent.FLIGHT, 200, 100, 200, 200), edge.resizeCanvas(400, 300).regions.single())
        assertEquals(small, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = small))).hudSceneLayout)
        assertFailsWith<IllegalArgumentException> { scene.resizeCanvas(239, 120) }
        assertFailsWith<IllegalArgumentException> { scene.resizeCanvas(240, 8193) }
    }

    @Test fun addAndRemoveRegionsKeepIdentitiesBoundsAndOtherSettings() {
        val base = HudSceneLayout(240, 120, listOf(HudRegion("region-1", HudRegionContent.FLIGHT, 0, 0, 240, 120, .3f)))
        val first = base.addRegion(HudRegionContent.ENGINE)
        val second = first.addRegion(HudRegionContent.ENGINE)
        assertEquals(listOf("region-1", "region-2", "region-3"), second.regions.map { it.id })
        assertEquals(listOf(1, 2), second.regions.filter { it.content == HudRegionContent.ENGINE }.map { it.engineIndex })
        assertEquals(base.regions.first(), second.regions.first())
        assertTrue(second.regions.all { it.x + it.width <= second.width && it.y + it.height <= second.height })
        val removed = second.removeRegion("region-2")
        assertEquals(listOf(base.regions.first(), second.regions.last()), removed.regions)
        assertEquals(removed, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = removed))).hudSceneLayout)
        assertFailsWith<IllegalArgumentException> { base.removeRegion("region-1") }
        var full = base
        repeat(31) { full = full.addRegion(HudRegionContent.ATTITUDE) }
        assertEquals(32, full.regions.size)
        assertFailsWith<IllegalArgumentException> { full.addRegion(HudRegionContent.ENGINE) }
    }

    @Test fun layoutsRoundTripAndResetWithoutChangingRuntimePreferences() {
        val original = AppSettings(hudEngineIndex = 2, hudClickThrough = false)
        val scene = HudSceneLayout.initial(original)
        val configured = original.copy(hudSceneLayout = scene)
        assertEquals(configured, SettingsJson.decode(SettingsJson.encode(configured)))
        assertNull(SettingsJson.decode(SettingsJson.encode(AppSettings())).hudSceneLayout)
        assertEquals(2, scene.regions.single { it.content == HudRegionContent.ENGINE }.engineIndex)
        assertNull(configured.withHudLayout(AppSettings()).hudSceneLayout)
        assertFalse(configured.withHudLayout(AppSettings()).hudClickThrough)
        assertEquals(scene, AppSettings().withHudLayout(configured).hudSceneLayout)
    }

    @Test fun rejectOffCanvasDuplicateAndInvalidAlphaConfigurations() {
        val region = HudRegion("one", HudRegionContent.FLIGHT, 10, 10, 200, 100)
        assertFailsWith<IllegalArgumentException> { HudSceneLayout(300, 200, listOf(region, region)) }
        assertFailsWith<IllegalArgumentException> { HudSceneLayout(300, 200, listOf(region.copy(x = 200))) }
        for (alpha in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { region.copy(backgroundAlpha = alpha) }
            assertFailsWith<IllegalArgumentException> { region.copy(contentAlpha = alpha) }
        }
        val layout = HudSceneLayout(300, 200, listOf(region))
        val json = SettingsJson.encode(AppSettings(hudSceneLayout = layout))
        assertFails { SettingsJson.decode(json.replace("\"FLIGHT\"", "\"unknown\"")) }
    }
}
