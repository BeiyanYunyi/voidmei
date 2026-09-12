package voidmei.config

import kotlin.test.*
import voidmei.telemetry.EngineFieldPreset

class LegacyEngineCreationTest {
    @Test fun createsIndependentPresetsBeforeApplyingPositionVisibilityAndColumns() {
        val flight = HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 100, 100)
        val base = HudSceneLayout(1000, 600, listOf(flight))
        val power = base.legacyEngineRegion("engineInfoSwitch")
        val controls = base.legacyEngineRegion("enableEngineControl")
        assertTrue(controls.x >= power.x + power.width)
        val parsed = LegacySettingsReader.read("""(panel "动力信息" :x .25 :y .2 :switch-key engineInfoSwitch :visible false
            (item c :target hudColumns :type slider :value 3))
            (panel "引擎控制" :x .5 :y .1 :switch-key enableEngineControl :visible true)""").copy(
            engineRegionsToCreate = listOf(power, controls), engineColumnsRegionId = power.id,
            enginePanelTargets = mapOf("engineInfoSwitch" to power.id, "enableEngineControl" to controls.id),
            enginePositionTargets = mapOf("engineInfoSwitch" to power.id, "enableEngineControl" to controls.id))
        val result = parsed.applyToScene(base)!!
        assertEquals(listOf(flight, power.copy(x = 250, y = 120, visible = false, readingColumns = 3),
            controls.copy(x = 500, y = 60)), result.regions)
        assertEquals(listOf(1, 1), result.regions.drop(1).map { it.engineIndex })
        assertEquals(EngineFieldPreset.POWER.fields, result.regions[1].fields)
        assertEquals(EngineFieldPreset.CONTROLS.fields, result.regions[2].fields)
        assertEquals(result, parsed.applyToScene(result))
        val current = AppSettings(hudSceneLayout = result)
        assertEquals(current, SettingsJson.decode(SettingsJson.encode(current)))
    }
    @Test fun noExistingRegionIsOverwrittenAndCapacityIsEnforced() {
        val base = HudSceneLayout(1000, 600, listOf(HudRegion("legacy-power", HudRegionContent.ENGINE, 0, 0, 100, 100)))
        val candidate = base.legacyEngineRegion("engineInfoSwitch")
        assertEquals("legacy-power-1", candidate.id)
        val selected = LegacySettings(null, null, null, engineRegionsToCreate = listOf(candidate))
        assertEquals(base.regions.first(), selected.applyToScene(base)!!.regions.first())
        val full = base.copy(regions = (1..32).map { base.regions.first().copy(id = "full-$it") })
        assertFails { selected.applyToScene(full) }
        assertFails { selected.copy(engineRegionsToCreate = listOf(candidate, candidate)).applyToScene(base) }
        assertFails { selected.copy(engineRegionsToCreate = listOf(candidate.copy(content = HudRegionContent.FLIGHT))).applyToScene(base) }
    }
}
