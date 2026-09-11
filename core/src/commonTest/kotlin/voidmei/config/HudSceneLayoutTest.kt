package voidmei.config

import kotlin.test.*

class HudSceneLayoutTest {
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
